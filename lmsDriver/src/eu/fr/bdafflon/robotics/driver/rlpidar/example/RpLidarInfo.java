package eu.fr.bdafflon.robotics.driver.rlpidar.example;

/**
 *
 * @author BDAFFLON
 */
public class RpLidarInfo {
	public int model;
	public int firmware_minor;
	public int firmware_major;
	public int hardware;
	public byte[] serialNumber = new byte[16];

	public String toString() {
		String ans="";
		ans+="DEVICE INFO \n";
		ans+="  model = " + model+"\n";
		ans+="  firmware_minor = " + firmware_minor+"\n";
		ans+="  firmware_major = " + firmware_major+"\n";
		ans+="  hardware = " + hardware+"\n";

		return ans;
	}
}
