package org.cryptomator.common.keychain;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.DisplayName;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests for KeyDerivationCalculator timing estimation.
 */
class KeyDerivationCalculatorTest {

	@Test
	@DisplayName("Calibration produces valid regression model")
	void testCalibration() {
		// Trigger calibration
		long estimate = KeyDerivationCalculator.estimateDerivationTime(100000);
		
		// Estimate should be reasonable (positive and less than 10 seconds)
		assertTrue(estimate > 0, "Estimated time should be positive");
		assertTrue(estimate < 10000, "Estimated time should be less than 10 seconds for 100k iterations");
		
		// Print calibration info for manual inspection
		System.out.println("=== Calibration Information ===");
		System.out.println(KeyDerivationCalculator.getCalibrationInfo());
	}
	
	@Test
	@DisplayName("Estimation accuracy test - compare estimate vs actual")
	void testEstimationAccuracy() {
		// Test various iteration counts
		int[] testPoints = {10000, 75000, 200000, 750000};
		
		System.out.println("\n=== Accuracy Comparison ===");
		System.out.println("Iterations | Estimated (ms) | Actual (ms) | Error (%)");
		System.out.println("-----------|----------------|-------------|----------");
		
		double totalError = 0;
		int count = 0;
		
		for (int iterations : testPoints) {
			long estimated = KeyDerivationCalculator.estimateDerivationTime(iterations);
			
			// Take median of 3 measurements for more stable comparison
			long[] measurements = new long[3];
			for (int i = 0; i < 3; i++) {
				measurements[i] = KeyDerivationCalculator.benchmarkIterations(iterations);
			}
			java.util.Arrays.sort(measurements);
			long actual = measurements[1]; // median
			
			double errorPercent = Math.abs(estimated - actual) * 100.0 / actual;
			totalError += errorPercent;
			count++;
			
			System.out.printf("%,10d | %,14d | %,11d | %6.2f%%%n", 
				iterations, estimated, actual, errorPercent);
		}
		
		double avgError = totalError / count;
		System.out.printf("\nAverage error: %.2f%%%n", avgError);
		
		// On average, error should be reasonable (< 50% allows for system variance)
		// Individual measurements can vary more due to JVM warmup, GC, etc.
		assertTrue(avgError < 50, 
			String.format("Average error is too high: %.2f%%", avgError));
	}
	
	@Test
	@DisplayName("Linear scaling verification")
	void testLinearScaling() {
		// Doubling iterations should approximately double time
		long time1 = KeyDerivationCalculator.estimateDerivationTime(100000);
		long time2 = KeyDerivationCalculator.estimateDerivationTime(200000);
		
		double ratio = (double) time2 / time1;
		
		System.out.println("\n=== Linear Scaling Test ===");
		System.out.printf("100k iterations: %d ms%n", time1);
		System.out.printf("200k iterations: %d ms%n", time2);
		System.out.printf("Ratio (should be ~2.0): %.3f%n", ratio);
		
		// Ratio should be close to 2.0 (allow 1.7-2.3 range for fixed overhead)
		assertTrue(ratio > 1.7 && ratio < 2.3, 
			"Doubling iterations should approximately double time");
	}
	
	@Test
	@DisplayName("Extreme values handling")
	void testExtremeValues() {
		// Very small
		long verySmall = KeyDerivationCalculator.estimateDerivationTime(100);
		assertTrue(verySmall >= 0, "Very small iteration count should give non-negative time");
		
		// Very large
		long veryLarge = KeyDerivationCalculator.estimateDerivationTime(10_000_000);
		assertTrue(veryLarge > 0, "Very large iteration count should give positive time");
		assertTrue(veryLarge > verySmall, "More iterations should take more time");
		
		System.out.println("\n=== Extreme Values ===");
		System.out.printf("100 iterations: %d ms%n", verySmall);
		System.out.printf("10M iterations: %d ms%n", veryLarge);
	}
	
	@Test
	@DisplayName("Security level classification")
	void testSecurityLevels() {
		System.out.println("\n=== Security Levels ===");
		int[] testCounts = {10000, 50000, 100000, 500000, 1000000, 5000000};
		
		for (int count : testCounts) {
			String level = KeyDerivationCalculator.getSecurityLevel(count);
			long time = KeyDerivationCalculator.estimateDerivationTime(count);
			String crackTime = KeyDerivationCalculator.estimateSimplePasswordCrackTime(count);
			
			System.out.printf("%,8d iterations: %-10s | Unlock: %,6d ms | Crack: %s%n",
				count, level, time, crackTime);
		}
	}
}

