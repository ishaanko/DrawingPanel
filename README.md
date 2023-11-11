# DrawingPanel

DrawingPanel is a simplified Java drawing window class to accompany Building Java Programs textbook and associated materials.

This is based on code written by Stuart Reges of UW and Marty Stepp.

This implementation includes performance enhancements!


Quick start: 

Include DrawingPanel.java in your project

Sample code to use DrawingPanel to create a circle:
```java
import java.awt.*;

public class DrawingPanelExample {
    public static void main(String[] args) {
        DrawingPanel panel = new DrawingPanel(500, 500);
        Graphics g = panel.getGraphics();

        panel.setBackground(Color.GRAY);

        g.setColor(Color.CYAN);
        g.fillOval(250, 250, 50, 50);
    }
}
```

Further documentation can be found on: https://www.buildingjavaprograms.com/code-files/4ed/javadoc/DrawingPanel.html
