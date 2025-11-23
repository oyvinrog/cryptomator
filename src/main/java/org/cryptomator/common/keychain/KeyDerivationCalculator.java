package org.cryptomator.common.keychain;

import javax.crypto.SecretKeyFactory;
import javax.crypto.spec.PBEKeySpec;
import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;
import java.util.concurrent.TimeUnit;

/**
 * Utility class for calculating key derivation timing and security estimates.
 * Provides time estimates for different PBKDF2 iteration counts and
 * estimates for brute-force attack resistance.
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
	
	// Cached benchmark results to avoid re-benchmarking on every call
	private static volatile Long cachedBaselineTime = null;
	private static final int BASELINE_ITERATIONS = 10000;
	
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
	 * Estimate time for key derivation based on iteration count.
	 * Uses a linear extrapolation from a cached baseline benchmark.
	 * The benchmark is only performed once and cached for performance.
	 * 
	 * @param iterations Number of PBKDF2 iterations
	 * @return Estimated time in milliseconds
	 */
	public static long estimateDerivationTime(int iterations) {
		// Use cached baseline if available, otherwise benchmark once
		if (cachedBaselineTime == null) {
			synchronized (KeyDerivationCalculator.class) {
				if (cachedBaselineTime == null) {
					cachedBaselineTime = benchmarkIterations(BASELINE_ITERATIONS);
				}
			}
		}
		
		// Linear extrapolation from cached baseline
		return (cachedBaselineTime * iterations) / BASELINE_ITERATIONS;
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
}

