import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
public class CaptureScreen {
    public static void main(String[] args) throws Exception {
        Robot robot = new Robot();
        BufferedImage screen = robot.createScreenCapture(new Rectangle(Toolkit.getDefaultToolkit().getScreenSize()));
        ImageIO.write(screen,"png",new File(args[0]));
    }
}
