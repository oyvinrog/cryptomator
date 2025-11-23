package org.cryptomator.common.keychain;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for calculating key derivation timing and security estimates.
 * Provides time estimates for different PBKDF2 iteration counts and
 * estimates for brute-force attack resistance.
 * 
 * <p>Timing Model: Uses Ordinary Least Squares (OLS) linear regression to fit
 * the theoretical model: T(n) = α + β·n, where:
 * <ul>
 *   <li>T(n) = total time for n iterations</li>
 *   <li>α = fixed overhead (setup, teardown costs)</li>
 *   <li>β = time per iteration</li>
 *   <li>n = iteration count</li>
 * </ul>
 * 
 * <p>This approach is superior to single-point extrapolation because:
 * <ol>
 *   <li>Accounts for fixed overhead that doesn't scale with iterations</li>
 *   <li>Uses multiple calibration points across different scales</li>
 *   <li>Employs statistical regression to minimize estimation error</li>
 *   <li>Provides robust estimates even for extreme iteration counts</li>
 * </ol>
 */
public class KeyDerivationCalculator {
	
	private static final String PBKDF2_ALGORITHM = "PBKDF2WithHmacSHA256";
	private static final int AES_KEY_SIZE = 256;
	
	// Password entropy estimates (bits) for different password types
	private static final double ENTROPY_6_DIGIT_PIN = 19.93; // log2(10^6)
	private static final double ENTROPY_8_CHAR_LOWERCASE = 37.60; // log2(26^8)
	private static final double ENTROPY_8_CHAR_MIXED = 52.56; // log2(62^8) - alphanumeric
	private static final double ENTROPY_12_CHAR_MIXED = 78.83; // log2(62^12)
	private static final double ENTROPY_16_CHAR_MIXED = 105.11; // log2(62^16)
	
	// Attack speed estimates (attempts per second)
	private static final long ATTACKS_PER_SEC_CONSUMER_GPU = 100_000; // Modern consumer GPU
	private static final long ATTACKS_PER_SEC_SUPERCOMPUTER = 1_000_000_000; // 1 billion attempts/sec
	
	/**
	 * Calibration points for multi-point regression.
	 * Logarithmically distributed to cover wide range efficiently:
	 * - Low: captures overhead and fast operations
	 * - Medium: typical usage range
	 * - High: validates linear scaling at higher counts
	 */
	private static final int[] CALIBRATION_POINTS = {
		5_000,      // Low - captures fixed overhead
		50_000,     // Medium-low - typical "fast" setting
		250_000,    // Medium-high - typical "high" setting  
		1_000_000   // High - validates scaling, typical "very high" setting
	};
	
	/**
	 * Number of repetitions per calibration point for statistical robustness.
	 * Median is used to reject outliers from JVM warmup or system interference.
	 */
	private static final int CALIBRATION_REPETITIONS = 3;
	
	/**
	 * Cached regression coefficients computed from calibration.
	 * Uses volatile for thread-safe lazy initialization with double-checked locking.
	 */
	private static volatile RegressionModel cachedModel = null;
	
	/**
	 * Simple linear regression model: y = alpha + beta * x
	 */
	private static class RegressionModel {
		final double alpha;  // Intercept (fixed overhead in ms)
		final double beta;   // Slope (time per iteration in ms)
		
		RegressionModel(double alpha, double beta) {
			this.alpha = alpha;
			this.beta = beta;
		}
		
		/**
		 * Predict time for given iteration count using the regression model.
		 * Ensures non-negative predictions (theoretical constraint).
		 */
		double predict(int iterations) {
			return Math.max(0, alpha + beta * iterations);
		}
	}
	
	private KeyDerivationCalculator() {
		// Utility class
	}
	
	/**
	 * Benchmark PBKDF2 key derivation for a specific iteration count.
	 * Performs actual key derivation to measure real-world performance.
	 * 
	 * @param iterations Number of PBKDF2 iterations
	 * @return Time taken in milliseconds
	 */
	public static long benchmarkIterations(int iterations) {
		try {
			char[] password = "benchmark".toCharArray();
			byte[] salt = new byte[32];
			
			long startTime = System.nanoTime();
			PBEKeySpec spec = new PBEKeySpec(password, salt, iterations, AES_KEY_SIZE);
			try {
				SecretKeyFactory factory = SecretKeyFactory.getInstance(PBKDF2_ALGORITHM);
				factory.generateSecret(spec).getEncoded();
			} finally {
				spec.clearPassword();
			}
			long endTime = System.nanoTime();
			
			return TimeUnit.NANOSECONDS.toMillis(endTime - startTime);
		} catch (NoSuchAlgorithmException | InvalidKeySpecException e) {
			throw new RuntimeException("Failed to benchmark key derivation", e);
		}
	}
	
	/**
	 * Perform calibration benchmarks and compute regression model.
	 * Uses Ordinary Least Squares (OLS) to fit the linear model: T(n) = α + β·n
	 * 
	 * <p>Statistical Method:
	 * Given paired observations (x_i, y_i) where x = iterations, y = time,
	 * OLS minimizes sum of squared residuals to find:
	 * <pre>
	 * β = Σ[(x_i - x̄)(y_i - ȳ)] / Σ[(x_i - x̄)²]
	 * α = ȳ - β·x̄
	 * </pre>
	 * 
	 * <p>This approach is optimal under Gauss-Markov theorem assumptions:
	 * linear relationship, homoscedastic errors, and independent observations.
	 * 
	 * @return Regression model fitted to calibration data
	 */
	private static RegressionModel calibrateModel() {
		List<Double> xValues = new ArrayList<>();  // Iteration counts
		List<Double> yValues = new ArrayList<>();  // Measured times
		
		// Perform JVM warmup: run one calibration pass to warm up JIT compiler
		// This reduces variance in actual measurements
		for (int iterations : CALIBRATION_POINTS) {
			benchmarkIterations(iterations);
		}
		
		// Collect calibration measurements
		for (int iterations : CALIBRATION_POINTS) {
			// Take multiple measurements and use median to reject outliers
			long[] measurements = new long[CALIBRATION_REPETITIONS];
			for (int rep = 0; rep < CALIBRATION_REPETITIONS; rep++) {
				measurements[rep] = benchmarkIterations(iterations);
			}
			
			// Use median (more robust to outliers than mean)
			Arrays.sort(measurements);
			long medianTime = measurements[CALIBRATION_REPETITIONS / 2];
			
			xValues.add((double) iterations);
			yValues.add((double) medianTime);
		}
		
		// Compute OLS regression coefficients
		int n = xValues.size();
		
		// Calculate means: x̄ and ȳ
		double xMean = xValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		double yMean = yValues.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
		
		// Calculate sums for regression formulas
		double sumXY = 0.0;  // Σ[(x_i - x̄)(y_i - ȳ)]
		double sumXX = 0.0;  // Σ[(x_i - x̄)²]
		
		for (int i = 0; i < n; i++) {
			double xDev = xValues.get(i) - xMean;
			double yDev = yValues.get(i) - yMean;
			sumXY += xDev * yDev;
			sumXX += xDev * xDev;
		}
		
		// Compute regression coefficients
		// β = covariance(x,y) / variance(x)
		double beta = sumXY / sumXX;
		
		// α = ȳ - β·x̄
		double alpha = yMean - beta * xMean;
		
		return new RegressionModel(alpha, beta);
	}
	
	/**
	 * Estimate time for key derivation based on iteration count.
	 * Uses multi-point OLS regression model for accurate prediction.
	 * 
	 * <p>The model is calibrated lazily on first use and cached for performance.
	 * Thread-safe initialization via double-checked locking.
	 * 
	 * <p>Theoretical basis: PBKDF2 time complexity is O(n) where n = iterations.
	 * The linear model T(n) = α + β·n captures:
	 * <ul>
	 *   <li>α: Fixed overhead (key factory setup, memory allocation, etc.)</li>
	 *   <li>β: Marginal cost per iteration (HMAC computation)</li>
	 * </ul>
	 * 
	 * @param iterations Number of PBKDF2 iterations
	 * @return Estimated time in milliseconds
	 */
	public static long estimateDerivationTime(int iterations) {
		// Lazy initialization with double-checked locking (Bloch's effective Java pattern)
		if (cachedModel == null) {
			synchronized (KeyDerivationCalculator.class) {
				if (cachedModel == null) {
					cachedModel = calibrateModel();
				}
			}
		}
		
		// Apply regression model to predict time
		double estimatedTime = cachedModel.predict(iterations);
		
		// Round to nearest millisecond (can't be more precise than that)
		return Math.round(estimatedTime);
	}
	
	/**
	 * Estimate time to brute-force a password with given entropy and iteration count.
	 * 
	 * @param iterations PBKDF2 iteration count
	 * @param passwordEntropyBits Entropy of the password in bits
	 * @param attacksPerSecond Attack speed (attempts per second)
	 * @return Estimated time to brute force in seconds
	 */
	public static double estimateBruteForceTime(int iterations, double passwordEntropyBits, long attacksPerSecond) {
		// Number of possible passwords = 2^entropy
		double totalAttempts = Math.pow(2, passwordEntropyBits);
		
		// Average time to find password = half of total attempts
		double avgAttempts = totalAttempts / 2;
		
		// Time per attempt with PBKDF2 iterations
		double timePerAttempt = estimateDerivationTime(iterations) / 1000.0; // Convert to seconds
		
		// Brute force with parallel attacks
		// Assumes attacker can perform attacksPerSecond attempts per second
		// But each attempt requires timePerAttempt, so effective rate is min(attacksPerSecond, 1/timePerAttempt)
		double effectiveRate = Math.min(attacksPerSecond, 1.0 / timePerAttempt);
		
		return avgAttempts / effectiveRate;
	}
	
	/**
	 * Format a time duration into human-readable form.
	 * 
	 * @param seconds Time in seconds
	 * @return Human-readable time string
	 */
	public static String formatTime(double seconds) {
		if (seconds < 0.001) {
			return "< 1 millisecond";
		} else if (seconds < 1) {
			return String.format("%.0f milliseconds", seconds * 1000);
		} else if (seconds < 60) {
			return String.format("%.1f seconds", seconds);
		} else if (seconds < 3600) {
			return String.format("%.1f minutes", seconds / 60);
		} else if (seconds < 86400) {
			return String.format("%.1f hours", seconds / 3600);
		} else if (seconds < 31536000) {
			return String.format("%.1f days", seconds / 86400);
		} else if (seconds < 31536000000L) {
			return String.format("%.1f years", seconds / 31536000);
		} else if (seconds < 31536000000000L) {
			return String.format("%.1f thousand years", seconds / 31536000000.0);
		} else if (seconds < 31536000000000000L) {
			return String.format("%.1f million years", seconds / 31536000000000.0);
		} else {
			return String.format("%.1f billion years", seconds / 31536000000000000.0);
		}
	}
	
	/**
	 * Calculate security level description based on iteration count.
	 * 
	 * @param iterations PBKDF2 iteration count
	 * @return Security level description
	 */
	public static String getSecurityLevel(int iterations) {
		if (iterations < 50000) {
			return "Low";
		} else if (iterations < 100000) {
			return "Standard";
		} else if (iterations < 500000) {
			return "High";
		} else if (iterations < 1000000) {
			return "Very High";
		} else {
			return "Maximum";
		}
	}
	
	/**
	 * Get estimate for brute-forcing an 8-character mixed alphanumeric password.
	 * This represents a "simple" password scenario.
	 * 
	 * @param iterations PBKDF2 iteration count
	 * @return Time estimate string for GPU attack
	 */
	public static String estimateSimplePasswordCrackTime(int iterations) {
		double seconds = estimateBruteForceTime(iterations, ENTROPY_8_CHAR_MIXED, ATTACKS_PER_SEC_CONSUMER_GPU);
		return formatTime(seconds);
	}
	
	/**
	 * Get estimate for brute-forcing an 8-character mixed alphanumeric password
	 * using a supercomputer.
	 * 
	 * @param iterations PBKDF2 iteration count
	 * @return Time estimate string for supercomputer attack
	 */
	public static String estimateSupercomputerCrackTime(int iterations) {
		double seconds = estimateBruteForceTime(iterations, ENTROPY_8_CHAR_MIXED, ATTACKS_PER_SEC_SUPERCOMPUTER);
		return formatTime(seconds);
	}
	
	/**
	 * Suggested iteration count values for different security needs.
	 */
	public static class Presets {
		public static final int FAST = 50000;           // ~50ms per unlock (low security)
		public static final int STANDARD = 100000;      // ~100ms per unlock (standard)
		public static final int HIGH = 500000;          // ~500ms per unlock (high security)
		public static final int VERY_HIGH = 1000000;    // ~1s per unlock (very high security)
		public static final int MAXIMUM = 5000000;      // ~5s per unlock (maximum security)
		
		private Presets() {}
	}
	
	/**
	 * Get a human-readable description of what the iteration count means.
	 * 
	 * @param iterations PBKDF2 iteration count
	 * @return Description string
	 */
	public static String getIterationDescription(int iterations) {
		long unlockTime = estimateDerivationTime(iterations);
		String crackTime = estimateSimplePasswordCrackTime(iterations);
		
		return String.format("Unlock time: ~%dms | Brute-force resistance (8-char password): %s", 
			unlockTime, crackTime);
	}
	
	/**
	 * Get calibration information for diagnostic purposes.
	 * Triggers calibration if not already performed.
	 * 
	 * @return Human-readable string describing the calibration model
	 */
	public static String getCalibrationInfo() {
		// Ensure model is calibrated
		estimateDerivationTime(100000);
		
		RegressionModel model = cachedModel;
		if (model == null) {
			return "Calibration not yet performed";
		}
		
		// Format the regression equation
		return String.format(
			"Timing Model: T(n) = %.4f + %.8f·n ms%n" +
			"  - Fixed overhead (α): %.4f ms%n" +
			"  - Time per iteration (β): %.8f ms%n" +
			"  - Calibration points: %s%n" +
			"  - Method: Ordinary Least Squares regression",
			model.alpha, model.beta,
			model.alpha,
			model.beta,
			Arrays.toString(CALIBRATION_POINTS)
		);
	}
	
	/**
	 * Force recalibration of the timing model.
	 * Useful if system conditions have changed significantly.
	 * This is rarely needed as the initial calibration is already robust.
	 */
	public static void recalibrate() {
		synchronized (KeyDerivationCalculator.class) {
			cachedModel = null;
		}
	}
}

