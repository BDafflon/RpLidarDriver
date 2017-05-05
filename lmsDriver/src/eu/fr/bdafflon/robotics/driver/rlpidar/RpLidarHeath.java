package eu.fr.bdafflon.robotics.driver.rlpidar;

/**
 * Single measurement data from LIDAR
 *
 * @author BDAFFLON
 */

public class RpLidarHeath {
	public int status;
	public int error_code;

	public String toString() {

		switch (status) {
		case 0:
			return "  Good";
		case 1:
			return "  Warning";

		case 2:
			return "  Error";

		default:
			return"  unknown = " + status;

		}
	}
}
