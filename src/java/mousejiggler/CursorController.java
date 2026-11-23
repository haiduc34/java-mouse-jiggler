package mousejiggler;

import java.awt.AWTException;
import java.awt.GraphicsDevice;
import java.awt.GraphicsEnvironment;
import java.awt.MouseInfo;
import java.awt.Point;
import java.awt.PointerInfo;
import java.awt.Rectangle;
import java.awt.Robot;

/**
 * Mouse cursor control based on {@link Robot} from the standard library.
 * Screen bounds are the union of all display bounds (the virtual screen);
 * right and bottom edges are exclusive.
 */
public final class CursorController {

    private final Robot robot;

    public CursorController() {
        try {
            this.robot = new Robot();
        } catch (AWTException e) {
            throw new CursorException("Платформа не позволяет управлять курсором: " + e.getMessage(), e);
        }
    }

    public Point getPosition() {
        PointerInfo info = MouseInfo.getPointerInfo();
        if (info == null) {
            throw new CursorException("Не удалось получить позицию курсора.", null);
        }
        return info.getLocation();
    }

    public Rectangle getBounds() {
        Rectangle union = null;
        GraphicsEnvironment environment = GraphicsEnvironment.getLocalGraphicsEnvironment();
        for (GraphicsDevice device : environment.getScreenDevices()) {
            Rectangle bounds = device.getDefaultConfiguration().getBounds();
            union = union == null ? bounds : union.union(bounds);
        }
        if (union == null) {
            throw new CursorException("Не удалось определить границы экрана.", null);
        }
        return union;
    }

    public void moveTo(Point point) {
        robot.mouseMove(point.x, point.y);
    }

    public static final class CursorException extends RuntimeException {
        CursorException(String message, Throwable cause) {
            super(message, cause);
        }
    }
}
