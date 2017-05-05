package eu.fr.bdafflon.robotics.driver.rlpidar;

public class Main {
	public static void main(String[] args){

		try {
			RpLidarDriver driver = new RpLidarDriver("/dev/ttyUSB0");
			driver.setVerbose(false);
			driver.sendReset();
			driver.pause(2000);
			driver.sendGetInfo(0);
			driver.pause(100);
			driver.sendGetHealth(0);
			driver.pause(100);

			driver.sendStartMotor(600);
			driver.sendScan(0);
			driver.pause(5000);
			driver.sendStopMotor();

			/*driver.pause(2000);
			driver.sendStartMotor(600);

			driver.sendScan(0);
			driver.pause(2000);

			driver.sendStartMotor(0);*/
		} catch (Exception e) {
			// TODO Auto-generated catch block
			e.printStackTrace();
		}
	}
}
