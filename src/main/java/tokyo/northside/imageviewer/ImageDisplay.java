// License: GPL. For details, see LICENSE file.
// SPDX-License-Identifier: GPL-2.0-or-later
package tokyo.northside.imageviewer;

import tokyo.northside.imageviewer.panorama.CameraPlane;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics;
import java.awt.Image;
import java.awt.Point;
import java.awt.Rectangle;
import java.awt.event.KeyListener;
import java.awt.event.KeyEvent;
import java.awt.event.MouseEvent;
import java.awt.event.MouseListener;
import java.awt.event.MouseMotionListener;
import java.awt.event.MouseWheelEvent;
import java.awt.event.MouseWheelListener;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;

import javax.swing.JComponent;

import org.joml.Math;


/**
 * This object is a responsible JComponent which lets you zoom and drag. It is
 * included in a object.
 *
 * @author nokutu
 * @see ImageDisplay
 */
public class ImageDisplay extends JComponent {

  private static final long serialVersionUID = 3369727203329307716L;
  private static final double PANORAMA_FOV = Math.toRadians(110);

  /**
   * The rectangle (in image coordinates) of the image that is visible. This
   * rectangle is calculated each time the zoom is modified
   */
  private volatile Rectangle visibleRect;

  /**
   * When a selection is done, the rectangle of the selection (in image
   * coordinates)
   */
  private Rectangle selectedRect;

  private boolean pano;

  private BufferedImage image;

  private BufferedImage offscreenImage;

  private CameraPlane cameraPlane;

  private class ImgDisplayKeyListener implements KeyListener {

    public void keyPressed(KeyEvent e) {
      if ((e.getKeyCode() == KeyEvent.VK_Q) && ((e.getModifiers() & KeyEvent.CTRL_MASK) != 0)) {
        System.exit(0);
      }
    }

    public void keyReleased(KeyEvent e){
    }

    public void keyTyped(KeyEvent e){
    }
  }

  private class ImgDisplayMouseListener implements MouseListener, MouseWheelListener, MouseMotionListener {
    private boolean mouseIsDragging;
    private long lastTimeForMousePoint;
    private Point mousePointInImg;

    private static final int PICTURE_DRAG_BUTTON = 3;
    private static final int PICTURE_OPTION_BUTTON = 2;
    private static final int PICTURE_ZOOM_BUTTON = 1;

    /**
     * Zoom in and out, trying to preserve the point of the image that was under
     * the mouse cursor at the same place
     */
    @Override
    public void mouseWheelMoved(MouseWheelEvent e) {
      Image image;
      Rectangle visibleRect;
      synchronized (ImageDisplay.this) {
        image = getImage();
        visibleRect = ImageDisplay.this.visibleRect;
      }
      this.mouseIsDragging = false;
      ImageDisplay.this.selectedRect = null;
      if (image != null && Math.min(getSize().getWidth(), getSize().getHeight()) > 0) {
        // Calculate the mouse cursor position in image coordinates, so that
        // we can center the zoom
        // on that mouse position.
        // To avoid issues when the user tries to zoom in on the image
        // borders, this point is not calculated
        // again if there was less than 1.5seconds since the last event.
        if (e.getWhen() - this.lastTimeForMousePoint > 1500 || this.mousePointInImg == null) {
          this.lastTimeForMousePoint = e.getWhen();
          this.mousePointInImg = comp2imgCoord(visibleRect, e.getX(), e.getY());
        }
        // Set the zoom to the visible rectangle in image coordinates
        if (e.getWheelRotation() > 0) {
          visibleRect.width = visibleRect.width * 3 / 2;
          visibleRect.height = visibleRect.height * 3 / 2;
        } else {
          visibleRect.width = visibleRect.width * 2 / 3;
          visibleRect.height = visibleRect.height * 2 / 3;
        }
        // Check that the zoom doesn't exceed 2:1
        if (visibleRect.width < getSize().width / 2) {
          visibleRect.width = getSize().width / 2;
        }
        if (visibleRect.height < getSize().height / 2) {
          visibleRect.height = getSize().height / 2;
        }
        // Set the same ratio for the visible rectangle and the display area
        int hFact = visibleRect.height * getSize().width;
        int wFact = visibleRect.width * getSize().height;
        if (hFact > wFact) {
          visibleRect.width = hFact / getSize().height;
        } else {
          visibleRect.height = wFact / getSize().width;
        }
        if (ImageDisplay.this.pano) {
          // The size of the visible rectangle is limited by the offscreenImage size.
          checkVisibleRectSize(offscreenImage, visibleRect);
          // Set the position of the visible rectangle, so that the mouse
          // cursor doesn't move on the image.
          Rectangle drawRect = calculateDrawImageRectangle(visibleRect);
          visibleRect.x = this.mousePointInImg.x
                  + ((drawRect.x - e.getX()) * visibleRect.width) / drawRect.width;
          visibleRect.y = this.mousePointInImg.y
                  + ((drawRect.y - e.getY()) * visibleRect.height) / drawRect.height;
          // The position is also limited by the image size
          checkVisibleRectPos(offscreenImage, visibleRect);
        } else {
          // The size of the visible rectangle is limited by the image size.
          checkVisibleRectSize(image, visibleRect);
          // Set the position of the visible rectangle, so that the mouse
          // cursor doesn't move on the image.
          Rectangle drawRect = calculateDrawImageRectangle(visibleRect);
          visibleRect.x = this.mousePointInImg.x
                  + ((drawRect.x - e.getX()) * visibleRect.width) / drawRect.width;
          visibleRect.y = this.mousePointInImg.y
                  + ((drawRect.y - e.getY()) * visibleRect.height) / drawRect.height;
          // The position is also limited by the image size
          checkVisibleRectPos(image, visibleRect);
        }
        synchronized (ImageDisplay.this) {
          ImageDisplay.this.visibleRect = visibleRect;
        }

        ImageDisplay.this.repaint();
      }
    }

    /** Center the display on the point that has been clicked */
    @Override
    public void mouseClicked(MouseEvent e) {
      // Move the center to the clicked point.
      BufferedImage image;
      Rectangle visibleRect;
      synchronized (ImageDisplay.this) {
        image = getImage();
        visibleRect = ImageDisplay.this.visibleRect;
      }
      if (image != null && Math.min(getSize().getWidth(), getSize().getHeight()) > 0) {
        if (ImageDisplay.this.pano) {
          if (e.getButton() == PICTURE_OPTION_BUTTON) {
            if (!ImageDisplay.this.visibleRect.equals(new Rectangle(0, 0,
                    offscreenImage.getWidth(null), offscreenImage.getHeight(null)))) {
              // Zoom to 1:1
              ImageDisplay.this.visibleRect = new Rectangle(0, 0,
                  offscreenImage.getWidth(null), offscreenImage.getHeight(null));
              ImageDisplay.this.repaint();
            }
          } else if (e.getButton() == PICTURE_DRAG_BUTTON) {
            cameraPlane.setRotation(comp2imgCoord(visibleRect, e.getX(), e.getY()));
            ImageDisplay.this.repaint();
          }
          return;
        } else {
          if (e.getButton() == PICTURE_OPTION_BUTTON) {
            if (!ImageDisplay.this.visibleRect.equals(new Rectangle(0, 0, image.getWidth(null), image.getHeight(null)))) {
              // Zooms to 1:1
              ImageDisplay.this.visibleRect = new Rectangle(0, 0,
                  image.getWidth(null), image.getHeight(null));
            } else {
              // Zooms to best fit.
              ImageDisplay.this.visibleRect = new Rectangle(
                  0,
                  (image.getHeight(null) - (image.getWidth(null) * getHeight()) / getWidth()) / 2,
                  image.getWidth(null),
                  (image.getWidth(null) * getHeight()) / getWidth()
              );
            }
            ImageDisplay.this.repaint();
            return;
          } else if (e.getButton() != PICTURE_DRAG_BUTTON) {
            return;
          }
          // Calculate the translation to set the clicked point the center of
          // the view.
          Point click = comp2imgCoord(visibleRect, e.getX(), e.getY());
          Point center = getCenterImgCoord(visibleRect);
          visibleRect.x += click.x - center.x;
          visibleRect.y += click.y - center.y;
          checkVisibleRectPos(image, visibleRect);
          synchronized (ImageDisplay.this) {
            ImageDisplay.this.visibleRect = visibleRect;
          }
          ImageDisplay.this.repaint();
        }
      }
    }

    /**
     * Initialize the dragging, either with button 1 (simple dragging) or button
     * 3 (selection of a picture part)
     */
    @Override
    public void mousePressed(MouseEvent e) {
      if (getImage() == null) {
        this.mouseIsDragging = false;
        ImageDisplay.this.selectedRect = null;
        return;
      }
      Image image;
      Rectangle visibleRect;
      synchronized (ImageDisplay.this) {
        image = ImageDisplay.this.image;
        visibleRect = ImageDisplay.this.visibleRect;
      }
      if (image == null)
        return;
      if (e.getButton() == PICTURE_DRAG_BUTTON) {
        this.mousePointInImg = comp2imgCoord(visibleRect, e.getX(), e.getY());
        this.mouseIsDragging = true;
        ImageDisplay.this.selectedRect = null;
      } else if (e.getButton() == PICTURE_ZOOM_BUTTON) {
        this.mousePointInImg = comp2imgCoord(visibleRect, e.getX(), e.getY());
        checkPointInVisibleRect(this.mousePointInImg, visibleRect);
        this.mouseIsDragging = false;
        ImageDisplay.this.selectedRect = new Rectangle(this.mousePointInImg.x, this.mousePointInImg.y, 0, 0);
        ImageDisplay.this.repaint();
      } else {
        this.mouseIsDragging = false;
        ImageDisplay.this.selectedRect = null;
      }
    }

    @Override
    public void mouseDragged(MouseEvent e) {
      if (!this.mouseIsDragging && ImageDisplay.this.selectedRect == null)
        return;
      BufferedImage image;
      Rectangle visibleRect;
      synchronized (ImageDisplay.this) {
        image = getImage();
        visibleRect = ImageDisplay.this.visibleRect;
      }
      if (image == null) {
        this.mouseIsDragging = false;
        ImageDisplay.this.selectedRect = null;
        return;
      }
      if (this.mouseIsDragging) {
        if (!ImageDisplay.this.pano) {
          Point p = comp2imgCoord(visibleRect, e.getX(), e.getY());
          visibleRect.x += this.mousePointInImg.x - p.x;
          visibleRect.y += this.mousePointInImg.y - p.y;
          checkVisibleRectPos(image, visibleRect);
          synchronized (ImageDisplay.this) {
            ImageDisplay.this.visibleRect = visibleRect;
          }
          ImageDisplay.this.repaint();
        }
      } else if (ImageDisplay.this.selectedRect != null) {
        Point p = comp2imgCoord(visibleRect, e.getX(), e.getY());
        checkPointInVisibleRect(p, visibleRect);
        Rectangle rect = new Rectangle(p.x < this.mousePointInImg.x ? p.x
            : this.mousePointInImg.x, p.y < this.mousePointInImg.y ? p.y
            : this.mousePointInImg.y, p.x < this.mousePointInImg.x ? this.mousePointInImg.x
            - p.x : p.x - this.mousePointInImg.x,
            p.y < this.mousePointInImg.y ? this.mousePointInImg.y - p.y : p.y
                - this.mousePointInImg.y);
        checkVisibleRectSize(image, rect);
        checkVisibleRectPos(image, rect);
        ImageDisplay.this.selectedRect = rect;
        ImageDisplay.this.repaint();
      }
    }

    @Override
    public void mouseReleased(MouseEvent e) {
      if (!this.mouseIsDragging && ImageDisplay.this.selectedRect == null)
        return;
      BufferedImage image;
      synchronized (ImageDisplay.this) {
        image = getImage();
      }
      if (image == null) {
        this.mouseIsDragging = false;
        ImageDisplay.this.selectedRect = null;
        return;
      }
      if (this.mouseIsDragging) {
        if (ImageDisplay.this.pano) {
          Point current = comp2imgCoord(visibleRect, e.getX(), e.getY());
          cameraPlane.setRotationFromDelta(mousePointInImg, current);
          ImageDisplay.this.repaint();
        }
        this.mouseIsDragging = false;
      } else if (ImageDisplay.this.selectedRect != null) {
        int oldWidth = ImageDisplay.this.selectedRect.width;
        int oldHeight = ImageDisplay.this.selectedRect.height;
        // Check that the zoom doesn't exceed 2:1
        if (ImageDisplay.this.selectedRect.width < getSize().width / 2) {
          ImageDisplay.this.selectedRect.width = getSize().width / 2;
        }
        if (ImageDisplay.this.selectedRect.height < getSize().height / 2) {
          ImageDisplay.this.selectedRect.height = getSize().height / 2;
        }
        // Set the same ratio for the visible rectangle and the display
        // area
        int hFact = ImageDisplay.this.selectedRect.height * getSize().width;
        int wFact = ImageDisplay.this.selectedRect.width * getSize().height;
        if (hFact > wFact) {
          ImageDisplay.this.selectedRect.width = hFact / getSize().height;
        } else {
          ImageDisplay.this.selectedRect.height = wFact / getSize().width;
        }
        // Keep the center of the selection
        if (ImageDisplay.this.selectedRect.width != oldWidth) {
          ImageDisplay.this.selectedRect.x -= (ImageDisplay.this.selectedRect.width - oldWidth) / 2;
        }
        if (ImageDisplay.this.selectedRect.height != oldHeight) {
          ImageDisplay.this.selectedRect.y -= (ImageDisplay.this.selectedRect.height - oldHeight) / 2;
        }
        checkVisibleRectSize(image, ImageDisplay.this.selectedRect);
        checkVisibleRectPos(image, ImageDisplay.this.selectedRect);
        synchronized (ImageDisplay.this) {
          ImageDisplay.this.visibleRect = ImageDisplay.this.selectedRect;
        }
        ImageDisplay.this.selectedRect = null;
        ImageDisplay.this.repaint();
      }
    }

    @Override
    public void mouseEntered(MouseEvent e) {
      // Do nothing, method is enforced by MouseListener
    }

    @Override
    public void mouseExited(MouseEvent e) {
      // Do nothing, method is enforced by MouseListener
    }

    @Override
    public void mouseMoved(MouseEvent e) {
      // Do nothing, method is enforced by MouseListener
    }

    private void checkPointInVisibleRect(Point p, Rectangle visibleRect) {
      if (p.x < visibleRect.x) {
        p.x = visibleRect.x;
      }
      if (p.x > visibleRect.x + visibleRect.width) {
        p.x = visibleRect.x + visibleRect.width;
      }
      if (p.y < visibleRect.y) {
        p.y = visibleRect.y;
      }
      if (p.y > visibleRect.y + visibleRect.height) {
        p.y = visibleRect.y + visibleRect.height;
      }
    }
  }

  /**
   * Main constructor.
   */
  public ImageDisplay() {
    ImgDisplayMouseListener mouseListener = new ImgDisplayMouseListener();
    addMouseListener(mouseListener);
    addMouseWheelListener(mouseListener);
    addMouseMotionListener(mouseListener);
    ImgDisplayKeyListener keyListener = new ImgDisplayKeyListener();
    addKeyListener(keyListener);
  }

  /**
   * Sets a new picture to be displayed.
   *
   * @param image The picture to be displayed.
   */
  public void setImage(BufferedImage image, boolean pano) {
    synchronized (this) {
      this.image = image;
      this.pano = pano;
      this.selectedRect = null;
      if (image != null) {
        Dimension s = getSize();
        if (this.pano) {
          this.visibleRect = new Rectangle(0, 0, s.width, s.height);
          offscreenImage = new BufferedImage(s.width, s.height, BufferedImage.TYPE_3BYTE_BGR);
          cameraPlane = new CameraPlane(s.width, s.height,
              (s.width / 2.0d) / Math.tan(PANORAMA_FOV / 2.0d));
          cameraPlane.mapping(image, offscreenImage);
        } else {
          this.visibleRect = new Rectangle(0, 0, image.getWidth(null),
                  image.getHeight(null));
        }
      }
    }
    repaint();
  }

  /**
   * Returns the picture that is being displayed
   *
   * @return The picture that is being displayed.
   */
  public BufferedImage getImage() {
    return this.image;
  }

  /**
   * Paints the visible part of the picture.
   */
  @Override
  public void paintComponent(Graphics g) {
    BufferedImage image;
    Rectangle visibleRect;
    synchronized (this) {
      image = this.image;
      visibleRect = this.visibleRect;
    }
    if (image == null) {
      g.setColor(Color.black);
      String noImageStr = "No image selected";
      Rectangle2D noImageSize = g.getFontMetrics(g.getFont()).getStringBounds(
          noImageStr, g);
      Dimension size = getSize();
      g.drawString(noImageStr,
          (int) ((size.width - noImageSize.getWidth()) / 2),
          (int) ((size.height - noImageSize.getHeight()) / 2));
    } else {
      Rectangle target;
      if (this.pano) {
        cameraPlane.mapping(image, offscreenImage);
        target = new Rectangle(0, 0, offscreenImage.getWidth(null), offscreenImage.getHeight(null));
        g.drawImage(offscreenImage, target.x, target.y, target.x + target.width, target.y
                + target.height, visibleRect.x, visibleRect.y, visibleRect.x
                + visibleRect.width, visibleRect.y + visibleRect.height, null);
      } else {
        target = calculateDrawImageRectangle(visibleRect);
        g.drawImage(image, target.x, target.y, target.x + target.width, target.y
                + target.height, visibleRect.x, visibleRect.y, visibleRect.x
                + visibleRect.width, visibleRect.y + visibleRect.height, null);
        if (this.selectedRect != null) {
          Point topLeft = img2compCoord(visibleRect, this.selectedRect.x,
                  this.selectedRect.y);
          Point bottomRight = img2compCoord(visibleRect, this.selectedRect.x
                  + this.selectedRect.width, this.selectedRect.y + this.selectedRect.height);
          g.setColor(new Color(128, 128, 128, 180));
          g.fillRect(target.x, target.y, target.width, topLeft.y - target.y);
          g.fillRect(target.x, target.y, topLeft.x - target.x, target.height);
          g.fillRect(bottomRight.x, target.y, target.x + target.width
                  - bottomRight.x, target.height);
          g.fillRect(target.x, bottomRight.y, target.width, target.y
                  + target.height - bottomRight.y);
          g.setColor(Color.black);
          g.drawRect(topLeft.x, topLeft.y, bottomRight.x - topLeft.x,
                  bottomRight.y - topLeft.y);
        }
      }
    }
  }

  private Point img2compCoord(Rectangle visibleRect, int xImg, int yImg) {
    Rectangle drawRect = calculateDrawImageRectangle(visibleRect);
    return new Point(drawRect.x + ((xImg - visibleRect.x) * drawRect.width)
            / visibleRect.width, drawRect.y
            + ((yImg - visibleRect.y) * drawRect.height) / visibleRect.height);
  }

  private Point comp2imgCoord(Rectangle visibleRect, int xComp, int yComp) {
    Rectangle drawRect = calculateDrawImageRectangle(visibleRect);
    return new Point(
            visibleRect.x + ((xComp - drawRect.x) * visibleRect.width) / drawRect.width,
            visibleRect.y + ((yComp - drawRect.y) * visibleRect.height) / drawRect.height
    );
  }

  private static Point getCenterImgCoord(Rectangle visibleRect) {
    return new Point(visibleRect.x + visibleRect.width / 2, visibleRect.y + visibleRect.height / 2);
  }

  private Rectangle calculateDrawImageRectangle(Rectangle visibleRect) {
    return calculateDrawImageRectangle(visibleRect, new Rectangle(0, 0, getSize().width, getSize().height));
  }

  /**
   * calculateDrawImageRectangle
   *
   * @param imgRect
   *          the part of the image that should be drawn (in image coordinates)
   * @param compRect
   *          the part of the component where the image should be drawn (in
   *          component coordinates)
   * @return the part of compRect with the same width/height ratio as the image
   */
  private static Rectangle calculateDrawImageRectangle(Rectangle imgRect, Rectangle compRect) {
    int x = 0;
    int y = 0;
    int w = compRect.width;
    int h = compRect.height;
    int wFact = w * imgRect.height;
    int hFact = h * imgRect.width;
    if (wFact != hFact) {
      if (wFact > hFact) {
        w = hFact / imgRect.height;
        x = (compRect.width - w) / 2;
      } else {
        h = wFact / imgRect.width;
        y = (compRect.height - h) / 2;
      }
    }
    return new Rectangle(x + compRect.x, y + compRect.y, w, h);
  }

  /**
   * Zooms to 1:1 and, if it is already in 1:1, to best fit.
   */
  public void zoomBestFitOrOne() {
    Image image;
    Rectangle visibleRect;
    synchronized (this) {
      image = this.image;
      visibleRect = this.visibleRect;
    }
    if (image == null)
      return;
    if (visibleRect.width != image.getWidth(null)
        || visibleRect.height != image.getHeight(null)) {
      // The display is not at best fit. => Zoom to best fit
      visibleRect = new Rectangle(0, 0, image.getWidth(null),
          image.getHeight(null));
    } else {
      // The display is at best fit => zoom to 1:1
      Point center = getCenterImgCoord(visibleRect);
      visibleRect = new Rectangle(center.x - getWidth() / 2, center.y
          - getHeight() / 2, getWidth(), getHeight());
      checkVisibleRectPos(image, visibleRect);
    }
    synchronized (this) {
      this.visibleRect = visibleRect;
    }
    repaint();
  }

  private static void checkVisibleRectPos(Image image, Rectangle visibleRect) {
    if (visibleRect.x < 0) {
      visibleRect.x = 0;
    }
    if (visibleRect.y < 0) {
      visibleRect.y = 0;
    }
    if (visibleRect.x + visibleRect.width > image.getWidth(null)) {
      visibleRect.x = image.getWidth(null) - visibleRect.width;
    }
    if (visibleRect.y + visibleRect.height > image.getHeight(null)) {
      visibleRect.y = image.getHeight(null) - visibleRect.height;
    }
  }

  private static void checkVisibleRectSize(Image image, Rectangle visibleRect) {
    if (visibleRect.width > image.getWidth(null)) {
      visibleRect.width = image.getWidth(null);
    }
    if (visibleRect.height > image.getHeight(null)) {
      visibleRect.height = image.getHeight(null);
    }
  }
}
