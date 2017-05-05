package eu.fr.bdafflon.robotics.driver.rlpidar;

/**
 * Single measurement data from LIDAR
 *
 * @author BDAFFLON
 */
public class RpLidarData {
	public boolean start;
	public int quality;
	public int angle;
	public int distance;

	public long timeMilli;

	public boolean isInvalid() {
		return distance == 0;
	}
}
