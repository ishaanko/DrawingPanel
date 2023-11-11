import javax.imageio.ImageIO;
import javax.swing.Timer;
import javax.swing.*;
import javax.swing.event.ChangeEvent;
import javax.swing.event.ChangeListener;
import javax.swing.event.MouseInputAdapter;
import javax.swing.event.MouseInputListener;
import javax.swing.filechooser.FileFilter;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.awt.image.ImageObserver;
import java.awt.image.PixelGrabber;
import java.io.*;
import java.net.NoRouteToHostException;
import java.net.SocketException;
import java.net.URL;
import java.net.UnknownHostException;
import java.util.List;
import java.util.*;

public final class DrawingPanel implements ImageObserver {
    private static final Color GRID_LINE_COLOR = new Color(64, 64, 64, 128);
    private static final Object LOCK = new Object();

    private static final boolean SAVE_SCALED_IMAGES = true;
    private static final int DELAY = 100;
    private static final int MAX_FRAMES = 100;
    private static final int MAX_SIZE = 10000;
    private static final int GRID_LINES_PX_GAP_DEFAULT = 10;

    private static final String VERSION             = "4.08 (2023/11/10)";
    private static final String ABOUT_MESSAGE       = "DrawingPanel\n"
            + "Graphical library class to support Building Java Programs textbook\n"
            + "written by Stuart Reges, University of Washington\n"
            + "and Marty Stepp\n\n"
            + "Performance enhancements and bug fixes by: Ishaan Kothari\n"
            + "Version: " + VERSION + "\n\n"
            + "Visit our web site at:\n"
            + "https://www.buildingjavaprograms.com/";
    private static final String ABOUT_MESSAGE_TITLE = "About DrawingPanel";
    private static final String COURSE_WEB_SITE     = "https://courses.cs.washington.edu/courses/cse142/CurrentQtr/drawingpanel.txt";
    private static final String TITLE               = "Drawing Panel";

    public static final int DEFAULT_WIDTH           = 500;

    public static final int DEFAULT_HEIGHT          = 400;

    public static final String ANIMATED_PROPERTY    = "drawingpanel.animated";

    public static final String ANIMATION_FILE_NAME  = "_drawingpanel_animation_save.txt";

    public static final String AUTO_ENABLE_ANIMATION_ON_SLEEP_PROPERTY = "drawingpanel.animateonsleep";

    public static final String DIFF_PROPERTY        = "drawingpanel.diff";

    public static final String HEADLESS_PROPERTY    = "drawingpanel.headless";

    public static final String MULTIPLE_PROPERTY    = "drawingpanel.multiple";

    public static final String SAVE_PROPERTY        = "drawingpanel.save";

    private static final List<DrawingPanel> INSTANCES = new ArrayList<>();

    private static boolean DEBUG = false;
    private static int instances = 0;
    private static final Boolean headless = null;
    private static final Boolean antiAliasDefault = true;
    private static Thread shutdownThread = null;

    static {
        try {
            String debugProp = String.valueOf(System.getProperty("drawingpanel.debug")).toLowerCase();
            DEBUG = DEBUG || "true".equalsIgnoreCase(debugProp)
                    || "on".equalsIgnoreCase(debugProp)
                    || "yes".equalsIgnoreCase(debugProp)
                    || "1".equals(debugProp);
        } catch (Throwable ignored) {
        }
    }

    private static void checkAnimationSettings() {
        try {
            File settingsFile = new File(ANIMATION_FILE_NAME);
            if (settingsFile.exists()) {
                Scanner input = new Scanner(settingsFile);
                String animationSaveFileName = input.nextLine();
                input.close();
                System.out.println("***");
                System.out.println("*** DrawingPanel saving animated GIF: " +
                        new File(animationSaveFileName).getName());
                System.out.println("***");
                settingsFile.delete();

                System.setProperty(ANIMATED_PROPERTY, "1");
                System.setProperty(SAVE_PROPERTY, animationSaveFileName);
            }
        } catch (Exception e) {
            if (DEBUG) {
                System.out.println("error checking animation settings: " + e);
            }
        }
    }

    private static void ensureInRange(String name, int value, int max) {
        if (value < 0 || value > max) {
            throw new IllegalArgumentException(name + " must be between " + 0
                    + " and " + max + ", but saw " + value);
        }
    }

    private static void ensureNotNull(String name, Object value) {
        if (value == null) {
            throw new NullPointerException("null value was passed for " + name);
        }
    }

    public static int getAlpha(int rgb) {
        return (rgb & 0xff000000) >> 24;
    }

    public static int getBlue(int rgb) {
        return (rgb & 0x000000ff);
    }

    public static int getGreen(int rgb) {
        return (rgb & 0x0000ff00) >> 8;
    }

    public static int getRed(int rgb) {
        return (rgb & 0x00ff0000) >> 16;
    }

    private static Boolean getPropertyBoolean() {
        try {
            String prop = System.getProperty(DrawingPanel.HEADLESS_PROPERTY);
            if (prop == null) {
                return null;
            } else {
                return false;
            }
        } catch (SecurityException e) {
            if (DEBUG) System.out.println("Security exception when trying to read " + DrawingPanel.HEADLESS_PROPERTY);
            return null;
        }
    }

    private static boolean hasProperty(String name) {
        try {
            return System.getProperty(name) != null;
        } catch (SecurityException e) {
            if (DEBUG) System.out.println("Security exception when trying to read " + name);
            return false;
        }
    }

    public static boolean isAntiAliasDefault() {
        return antiAliasDefault;
    }

    public static boolean isHeadless() {
        return Objects.requireNonNullElseGet(headless, () -> hasProperty(HEADLESS_PROPERTY) && Boolean.TRUE.equals(getPropertyBoolean()));
    }

    public static boolean mainIsActive() {
        ThreadGroup group = Thread.currentThread().getThreadGroup();
        int activeCount = group.activeCount();

        Thread[] threads = new Thread[activeCount];
        group.enumerate(threads);
        for (Thread thread : threads) {
            String name = String.valueOf(thread.getName()).toLowerCase();
            if (DEBUG)
                System.out.println("    DrawingPanel.mainIsActive(): " + thread.getName() + ", priority=" + thread.getPriority() + ", alive=" + thread.isAlive() + ", stack=" + java.util.Arrays.toString(thread.getStackTrace()));
            if (name.contains("main") || name.contains("testrunner-assignmentrunner")) {
                return thread.isAlive();
            }
        }
        return false;
    }

    private static boolean propertyIsTrue(String name) {
        try {
            String prop = System.getProperty(name);
            return prop != null && (prop.equalsIgnoreCase("true")
                    || prop.equalsIgnoreCase("yes")
                    || prop.equalsIgnoreCase("1"));
        } catch (SecurityException e) {
            if (DEBUG) System.out.println("Security exception when trying to read " + name);
            return false;
        }
    }

    private static boolean usingDrJava() {
        try {
            return System.getProperty("drjava.debug.port") != null ||
                    System.getProperty("java.class.path").toLowerCase().contains("drjava");
        } catch (SecurityException e) {
            return false;
        }
    }

    private ActionListener actionListener;
    private List<ImageFrame> frames;
    private boolean animated = false;
    private boolean antialias = isAntiAliasDefault();
    private boolean gridLines = false;
    private BufferedImage image;
    private Color backgroundColor = Color.WHITE;
    private Gif89Encoder encoder;
    private Graphics2D g2;
    private ImagePanel imagePanel;
    private int currentZoom = 1;
    private int gridLinesPxGap = GRID_LINES_PX_GAP_DEFAULT;
    private final int instanceNumber;
    private int width;
    private int height;
    private JFileChooser chooser;
    private JFrame frame;
    private JLabel statusBar;
    private JPanel panel;
    private long createTime;
    private String callingClassName;
    private Timer timer;

    public DrawingPanel() {
        this(DEFAULT_WIDTH, DEFAULT_HEIGHT);
    }

    public DrawingPanel(int width, int height) {
        ensureInRange("width", width, MAX_SIZE);
        ensureInRange("height", height, MAX_SIZE);

        checkAnimationSettings();

        if (DEBUG) System.out.println("DrawingPanel(): going to grab lock");
        synchronized (LOCK) {
            instances++;
            instanceNumber = instances;
            INSTANCES.add(this);

            if (shutdownThread == null && !usingDrJava()) {
                if (DEBUG) System.out.println("DrawingPanel(): starting idle thread");

                shutdownThread = new Thread(() -> {
                    boolean save = shouldSave();
                    try {
                        while (true) {

                            save |= shouldSave();
                            if (DEBUG) System.out.println("DrawingPanel idle thread: instances=" + instances + ", save=" + save + ", main active=" + mainIsActive());
                            if ((instances == 0 || save) && !mainIsActive()) {
                                try {
                                    System.exit(0);
                                } catch (SecurityException sex) {
                                    if (DEBUG) System.out.println("DrawingPanel idle thread: unable to exit program: " + sex);
                                }
                            }

                            Thread.sleep(250);
                        }
                    } catch (Exception e) {
                        if (DEBUG) System.out.println("DrawingPanel idle thread: exception caught: " + e);
                    }
                });

                shutdownThread.setName("DrawingPanel-shutdown");
                shutdownThread.start();
            }
        }

        this.width = width;
        this.height = height;

        if (DEBUG) System.out.println("DrawingPanel(w=" + width + ",h=" + height + ",anim=" + isAnimated() + ",graph=" + isGraphical() + ",save=" + shouldSave());

        if (isAnimated() && shouldSave()) {

            image = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);

            antialias = false;

            Graphics g = image.getGraphics();
            g.setColor(backgroundColor);
            g.fillRect(0, 0, width + 1, height + 1);
        } else {
            image = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        }

        g2 = (Graphics2D) image.getGraphics();

        g2.setColor(Color.BLACK);
        if (antialias) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }

        if (isAnimated()) {
            initializeAnimation();
        }

        if (isGraphical()) {
            try {
                UIManager.setLookAndFeel(UIManager.getSystemLookAndFeelClassName());
            } catch (Exception ignored) {

            }

            statusBar = new JLabel(" ");
            statusBar.setBorder(BorderFactory.createLineBorder(Color.BLACK));

            panel = new JPanel(new FlowLayout(FlowLayout.LEFT, 0, 0));
            panel.setBackground(backgroundColor);
            panel.setPreferredSize(new Dimension(width, height));
            imagePanel = new ImagePanel(image);
            imagePanel.setBackground(backgroundColor);
            panel.add(imagePanel);

            MouseInputListener mouseListener = new DPMouseListener();
            panel.addMouseMotionListener(mouseListener);

            frame = new JFrame(TITLE);

            WindowListener windowListener = new DPWindowListener();
            frame.addWindowListener(windowListener);
            JScrollPane center = new JScrollPane(panel);
            frame.getContentPane().add(center);
            frame.getContentPane().add(statusBar, "South");
            frame.setBackground(Color.DARK_GRAY);

            actionListener = new DPActionListener();
            setupMenuBar();

            frame.pack();
            center(frame);
            frame.setVisible(true);
            if (!shouldSave()) {
                toFront(frame);
            }

            createTime = System.currentTimeMillis();
            timer = new Timer(DELAY, actionListener);
            timer.start();
        } else if (shouldSave()) {

            callingClassName = getCallingClassName();
            try {

                Runtime.getRuntime().addShutdownHook(new Thread(() -> {
                    if (DEBUG) System.out.println("DrawingPanel.run(): Running shutdown hook");
                    if (DEBUG) System.out.println("DrawingPanel shutdown hook: instances=" + instances);
                    try {
                        String filename = System.getProperty(SAVE_PROPERTY);
                        if (filename == null) {
                            filename = callingClassName + ".png";
                        }

                        if (isAnimated()) {
                            saveAnimated(filename);
                        } else {
                            save(filename);
                        }
                    } catch (SecurityException e) {
                        System.err.println("Security error while saving image: " + e);
                    } catch (IOException e) {
                        System.err.println("Error saving image: " + e);
                    }
                }));
            } catch (Exception e) {
                if (DEBUG) System.out.println("DrawingPanel(): unable to add shutdown hook: " + e);
            }
        }
    }

    public DrawingPanel(File imageFile) {
        this(imageFile.toString());
    }

    public DrawingPanel(String imageFileName) {
        this();
        Image image = loadImage(imageFileName);
        setSize(image.getWidth(this), image.getHeight(this));
        getGraphics().drawImage(image, 0, 0, this);
    }

    public void addKeyListener(KeyListener listener) {
        ensureNotNull("listener", listener);
        frame.addKeyListener(listener);
        panel.setFocusable(false);
        frame.requestFocusInWindow();
        frame.requestFocus();
    }

    public void addMouseListener(MouseListener listener) {
        ensureNotNull("listener", listener);
        panel.addMouseListener(listener);
        if (listener instanceof MouseMotionListener) {
            panel.addMouseMotionListener((MouseMotionListener) listener);
        }
    }

    private boolean autoEnableAnimationOnSleep() {
        return propertyIsTrue(AUTO_ENABLE_ANIMATION_ON_SLEEP_PROPERTY);
    }

    private void center(Window frame) {
        Toolkit tk = Toolkit.getDefaultToolkit();
        Dimension screen = tk.getScreenSize();
        int x = Math.max(0, (screen.width - frame.getWidth()) / 2);
        int y = Math.max(0, (screen.height - frame.getHeight()) / 2);
        frame.setLocation(x, y);
    }

    private void checkChooser() {
        if (chooser == null) {
            chooser = new JFileChooser();
            try {
                chooser.setCurrentDirectory(new File(System.getProperty("user.dir")));
            } catch (Exception ignored) {

            }
            chooser.setMultiSelectionEnabled(false);
            chooser.setFileFilter(new DPFileFilter());
        }
    }

    private void compareToFile() {

        try {
            String tempFile = saveToTempFile();

            checkChooser();
            if (chooser.showOpenDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            new DiffImage(chooser.getSelectedFile().toString(), tempFile);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(frame,
                    "Unable to compare images: \n" + ioe);
        }
    }

    private void compareToURL() {

        try {
            String tempFile = saveToTempFile();

            URL url = new URL(COURSE_WEB_SITE);
            Scanner input = new Scanner(url.openStream());
            List<String> lines = new ArrayList<>();
            List<String> filenames = new ArrayList<>();
            while (input.hasNextLine()) {
                String line = input.nextLine().trim();
                if (line.isEmpty()) { continue; }

                if (line.startsWith("#")) {

                    if (line.endsWith(":")) {

                        lines.add(line);
                        line = line.replaceAll("#\\s*", "");
                        filenames.add(line);
                    }
                } else {
                    lines.add(line);

                    int lastSlash = line.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        line = line.substring(lastSlash + 1);
                    }

                    int dot = line.lastIndexOf('.');
                    if (dot >= 0) {
                        line = line.substring(0, dot);
                    }

                    filenames.add(line);
                }
            }
            input.close();

            if (filenames.isEmpty()) {
                JOptionPane.showMessageDialog(frame,
                        "No valid web files found to compare against.",
                        "Error: no web files found",
                        JOptionPane.ERROR_MESSAGE);
            } else {
                String fileURL;
                if (filenames.size() == 1) {

                    fileURL = lines.get(0);
                } else {

                    int choice = showOptionDialog(frame,
                            filenames.toArray(new String[0]));
                    if (choice < 0) {
                        return;
                    }

                    fileURL = lines.get(choice);
                }
                new DiffImage(fileURL, tempFile);
            }
        } catch (NoRouteToHostException nrthe) {
            JOptionPane.showMessageDialog(frame, "You do not appear to have a working internet connection.\nPlease check your internet settings and try again.\n\n" + nrthe);
        } catch (UnknownHostException | SocketException uhe) {
            JOptionPane.showMessageDialog(frame, "Internet connection error: \n" + uhe);
        } catch (IOException ioe) {
            JOptionPane.showMessageDialog(frame, "Unable to compare images: \n" + ioe);
        }
    }

    private void exit() {
        if (isGraphical()) {
            frame.setVisible(false);
            frame.dispose();
        }
        try {
            System.exit(0);
        } catch (SecurityException ignored) {

        }
    }

    private String getCallingClassName() {
        StackTraceElement[] stack = new RuntimeException().getStackTrace();
        String className = this.getClass().getName();
        for (StackTraceElement element : stack) {
            String cl = element.getClassName();
            if (!className.equals(cl)) {
                className = cl;
                break;
            }
        }

        return className;
    }

    public Graphics2D getGraphics() {
        return g2;
    }

    private BufferedImage getImage() {

        BufferedImage image2;
        if (isAnimated()) {
            image2 = new BufferedImage(width, height, BufferedImage.TYPE_BYTE_INDEXED);
        } else {
            image2 = new BufferedImage(width, height, image.getType());
        }
        Graphics g = image2.getGraphics();

        g.setColor(backgroundColor);
        g.fillRect(0, 0, width, height);
        g.drawImage(image, 0, 0, panel);
        return image2;
    }

    public int getHeight() {
        return height;
    }

    public int getPixelRGB(int x, int y) {
        ensureInRange("x", x, getWidth() - 1);
        ensureInRange("y", y, getHeight() - 1);
        int rgb = image.getRGB(x, y);
        if (getAlpha(rgb) == 0) {
            return backgroundColor.getRGB();
        } else {
            return rgb;
        }
    }

    public int getWidth() {
        return width;
    }

    @Override
    public boolean imageUpdate(Image img, int infoflags, int x, int y, int width, int height) {
        if (imagePanel != null) {
            imagePanel.imageUpdate(img, infoflags, x, y, width, height);
        }
        return false;
    }

    private void initializeAnimation() {
        frames = new ArrayList<>();
        encoder = new Gif89Encoder();
    }

    private boolean isAnimated() {
        return animated || propertyIsTrue(ANIMATED_PROPERTY);
    }

    private boolean isGraphical() {
        return !hasProperty(SAVE_PROPERTY) && !isHeadless();
    }

    private boolean isMultiple() {
        return propertyIsTrue(MULTIPLE_PROPERTY);
    }

    public Image loadImage(String filename) {
        ensureNotNull("filename", filename);
        if (!(new File(filename)).exists()) {
            throw new RuntimeException("DrawingPanel.loadImage: File not found: " + filename);
        }
        Image img = Toolkit.getDefaultToolkit().getImage(filename);
        MediaTracker mt = new MediaTracker(imagePanel == null ? new JPanel() : imagePanel);
        mt.addImage(img, 0);
        try {
            mt.waitForID(0);
        } catch (InterruptedException ignored) {

        }
        return img;
    }

    public void onClick(DPMouseEventHandler e) {
        onMouseClick(e);
    }

    public void onEnter(DPMouseEventHandler e) {
        onMouseEnter(e);
    }

    public void onMouseClick(DPMouseEventHandler e) {
        ensureNotNull("event handler", e);
        DPMouseEventHandlerAdapter adapter = new DPMouseEventHandlerAdapter(e, "click");
        addMouseListener(adapter);
    }

    public void onMouseDrag(DPMouseEventHandler e) {
        ensureNotNull("event handler", e);
        DPMouseEventHandlerAdapter adapter = new DPMouseEventHandlerAdapter(e, "drag");
        addMouseListener(adapter);
    }

    public void onMouseEnter(DPMouseEventHandler e) {
        ensureNotNull("event handler", e);
        DPMouseEventHandlerAdapter adapter = new DPMouseEventHandlerAdapter(e, "enter");
        addMouseListener(adapter);
    }

    public void onMouseExit(DPMouseEventHandler e) {
        ensureNotNull("event handler", e);
        DPMouseEventHandlerAdapter adapter = new DPMouseEventHandlerAdapter(e, "exit");
        addMouseListener(adapter);
    }

    public void onMouseMove(DPMouseEventHandler e) {
        ensureNotNull("event handler", e);
        DPMouseEventHandlerAdapter adapter = new DPMouseEventHandlerAdapter(e, "move");
        addMouseListener(adapter);
    }

    public void onMove(DPMouseEventHandler e) {
        onMouseMove(e);
    }

    private boolean readyToClose() {
        return (instances == 0 || shouldSave()) && !mainIsActive();
    }

    private void replaceColor(BufferedImage image, Color oldColor, Color newColor) {
        int oldRGB = oldColor.getRGB();
        int newRGB = newColor.getRGB();
        for (int y = 0; y < image.getHeight(); y++) {
            for (int x = 0; x < image.getWidth(); x++) {
                if (image.getRGB(x, y) == oldRGB) {
                    image.setRGB(x, y, newRGB);
                }
            }
        }
    }

    public void save(File file) throws IOException {
        ensureNotNull("file", file);
        save(file.toString());
    }

    public void save(String filename) throws IOException {
        ensureNotNull("filename", filename);
        BufferedImage image2 = getImage();

        if (SAVE_SCALED_IMAGES && currentZoom != 1) {
            BufferedImage zoomedImage = new BufferedImage(width * currentZoom, height * currentZoom, image.getType());
            Graphics2D g = (Graphics2D) zoomedImage.getGraphics();
            g.setColor(Color.BLACK);
            if (antialias) {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            }
            g.scale(currentZoom, currentZoom);
            g.drawImage(image2, 0, 0, imagePanel);
            image2 = zoomedImage;
        }

        if (isMultiple()) {
            filename = filename.replaceAll("\\*", String.valueOf(instanceNumber));
        }

        int lastDot = filename.lastIndexOf(".");
        String extension = filename.substring(lastDot + 1);

        try {
            ImageIO.write(image2, extension, new File(filename));
        } catch (NullPointerException | FileNotFoundException ignored) {

        }

    }

    public void saveAnimated(File file) throws IOException {
        ensureNotNull("file", file);
        saveAnimated(file.toString());
    }

    public void saveAnimated(String filename) throws IOException {
        ensureNotNull("filename", filename);

        if (DEBUG) System.out.println("DrawingPanel.saveAnimated(" + filename + ")");
        frames.add(new ImageFrame(getImage(), 5000));

        try {
            for (int i = 0; i < frames.size(); i++) {
                ImageFrame imageFrame = frames.get(i);
                encoder.addFrame(imageFrame.image);
                encoder.getFrameAt(i).setDelay(imageFrame.delay);
                imageFrame.image.flush();
                frames.set(i, null);
            }
        } catch (OutOfMemoryError e) {
            System.out.println("Out of memory when saving");
        }

        encoder.setLoopCount(0);
        encoder.encode(new FileOutputStream(filename));
    }

    private void saveAs() {
        String filename = saveAsHelper("png");
        if (filename != null) {
            try {
                save(filename);
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to save image:\n" + ex);
            }
        }
    }

    private void saveAsAnimated() {
        String filename = saveAsHelper("gif");
        if (filename != null) {
            try {

                PrintStream out = new PrintStream(ANIMATION_FILE_NAME);
                out.println(filename);
                out.close();

                JOptionPane.showMessageDialog(frame,
                        "Due to constraints about how DrawingPanel works, you'll need to\n" +
                                "re-run your program.  When you run it the next time, DrawingPanel will \n" +
                                "automatically save your animated image as: " + new File(filename).getName()
                );
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to store animation settings:\n" + ex);
            }
        }
    }

    private String saveAsHelper(String extension) {

        checkChooser();
        if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
            return null;
        }

        File selectedFile = chooser.getSelectedFile();
        String filename = selectedFile.toString();
        if (!filename.toLowerCase().endsWith(extension)) {

            filename += "." + extension;
        }

        if (new File(filename).exists() && JOptionPane.showConfirmDialog(
                frame, "File exists.  Overwrite?", "Overwrite?",
                JOptionPane.YES_NO_OPTION) != JOptionPane.YES_OPTION) {
            return null;
        }

        return filename;
    }

    private String saveToTempFile() throws IOException {
        File currentImageFile = File.createTempFile("current_image", ".png");
        save(currentImageFile.toString());
        return currentImageFile.toString();
    }

    public void setBackground(Color c) {
        ensureNotNull("color", c);
        Color oldBackgroundColor = backgroundColor;
        backgroundColor = c;
        if (isGraphical()) {
            panel.setBackground(c);
            imagePanel.setBackground(c);
        }

        if (isAnimated()) {
            replaceColor(image, oldBackgroundColor, c);
        }
    }

    public void setGridLines(boolean gridLines) {
        setGridLines(gridLines, GRID_LINES_PX_GAP_DEFAULT);
    }

    void setGridLines(boolean gridLines, int pxGap) {
        this.gridLines = gridLines;
        this.gridLinesPxGap = pxGap;
        if (imagePanel != null) {
            imagePanel.repaint();
        }
    }

    public void setSize(int width, int height) {
        ensureInRange("width", width, MAX_SIZE);
        ensureInRange("height", height, MAX_SIZE);

        BufferedImage newImage = new BufferedImage(width, height, image.getType());
        if (imagePanel != null) {
            imagePanel.setImage(newImage);
        }
        newImage.getGraphics().drawImage(image, 0, 0, imagePanel == null ? new JPanel() : imagePanel);

        this.width = width;
        this.height = height;
        image = newImage;
        g2 = (Graphics2D) newImage.getGraphics();
        g2.setColor(Color.BLACK);
        if (antialias) {
            g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        }
        zoom(currentZoom);
        if (isGraphical()) {
            frame.pack();
        }
    }

    private void setStatusBarText(String text) {
        if (currentZoom != 1) {
            text += " (current zoom: " + currentZoom + "x" + ")";
        }
        statusBar.setText(text);
    }

    private void setupMenuBar() {

        final boolean secure = false;

        JMenuItem saveAs = new JMenuItem("Save As...", 'A');
        saveAs.addActionListener(actionListener);
        saveAs.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
        saveAs.setEnabled(!secure);

        JMenuItem saveAnimated = new JMenuItem("Save Animated GIF...", 'G');
        saveAnimated.addActionListener(actionListener);
        saveAnimated.setAccelerator(KeyStroke.getKeyStroke("ctrl A"));
        saveAnimated.setEnabled(!secure);

        JMenuItem compare = new JMenuItem("Compare to File...", 'C');
        compare.addActionListener(actionListener);
        compare.setEnabled(!secure);

        JMenuItem compareURL = new JMenuItem("Compare to Web File...", 'U');
        compareURL.addActionListener(actionListener);
        compareURL.setAccelerator(KeyStroke.getKeyStroke("ctrl U"));
        compareURL.setEnabled(!secure);

        JMenuItem zoomIn = new JMenuItem("Zoom In", 'I');
        zoomIn.addActionListener(actionListener);
        zoomIn.setAccelerator(KeyStroke.getKeyStroke("ctrl EQUALS"));

        JMenuItem zoomOut = new JMenuItem("Zoom Out", 'O');
        zoomOut.addActionListener(actionListener);
        zoomOut.setAccelerator(KeyStroke.getKeyStroke("ctrl MINUS"));

        JMenuItem zoomNormal = new JMenuItem("Zoom Normal (100%)", 'N');
        zoomNormal.addActionListener(actionListener);
        zoomNormal.setAccelerator(KeyStroke.getKeyStroke("ctrl 0"));

        JCheckBoxMenuItem gridLinesItem = new JCheckBoxMenuItem("Grid Lines");
        gridLinesItem.setMnemonic('G');
        gridLinesItem.setSelected(gridLines);
        gridLinesItem.addActionListener(actionListener);
        gridLinesItem.setAccelerator(KeyStroke.getKeyStroke("ctrl G"));

        JMenuItem exit = new JMenuItem("Exit", 'x');
        exit.addActionListener(actionListener);

        JMenuItem about = new JMenuItem("About...", 'A');
        about.addActionListener(actionListener);

        JMenu file = new JMenu("File");
        file.setMnemonic('F');
        file.add(compareURL);
        file.add(compare);
        file.addSeparator();
        file.add(saveAs);
        file.add(saveAnimated);
        file.addSeparator();
        file.add(exit);

        JMenu view = new JMenu("View");
        view.setMnemonic('V');
        view.add(zoomIn);
        view.add(zoomOut);
        view.add(zoomNormal);
        view.addSeparator();
        view.add(gridLinesItem);

        JMenu help = new JMenu("Help");
        help.setMnemonic('H');
        help.add(about);

        JMenuBar bar = new JMenuBar();
        bar.add(file);
        bar.add(view);
        bar.add(help);
        frame.setJMenuBar(bar);
    }

    private boolean shouldDiff() {
        return hasProperty(DIFF_PROPERTY);
    }

    private boolean shouldSave() {
        return hasProperty(SAVE_PROPERTY);
    }

    private int showOptionDialog(Frame parent,
                                 final String[] names) {
        final JDialog dialog = new JDialog(parent, "File to compare against?", true);
        JPanel center = new JPanel(new GridLayout(0, 1));

        final int[] hack = {-1};

        for (int i = 0; i < names.length; i++) {
            if (names[i].endsWith(":")) {
                center.add(new JLabel("<html><b>" + names[i] + "</b></html>"));
            } else {
                final JButton button = new JButton(names[i]);
                button.setActionCommand(String.valueOf(i));
                button.addActionListener(e -> {
                    hack[0] = Integer.parseInt(button.getActionCommand());
                    dialog.setVisible(false);
                });
                center.add(button);
            }
        }

        JPanel south = new JPanel();
        JButton cancel = new JButton("Cancel");
        cancel.setMnemonic('C');
        cancel.requestFocus();
        cancel.addActionListener(e -> dialog.setVisible(false));
        south.add(cancel);

        dialog.setDefaultCloseOperation(JDialog.DO_NOTHING_ON_CLOSE);
        dialog.getContentPane().setLayout(new BorderLayout(10, 5));

        JLabel messageLabel = new JLabel("Choose File");
        dialog.add(messageLabel, BorderLayout.NORTH);
        dialog.add(center);
        dialog.add(south, BorderLayout.SOUTH);
        dialog.pack();
        dialog.setResizable(false);
        center(dialog);
        cancel.requestFocus();
        dialog.setVisible(true);
        cancel.requestFocus();

        return hack[0];
    }


    private void toFront(final Window window) {
        EventQueue.invokeLater(() -> {
            if (window != null) {
                window.toFront();
                window.repaint();
            }
        });
    }

    public void zoom(int zoomFactor) {
        currentZoom = Math.max(1, zoomFactor);
        if (isGraphical()) {
            Dimension size = new Dimension(width * currentZoom, height * currentZoom);
            imagePanel.setPreferredSize(size);
            panel.setPreferredSize(size);
            imagePanel.validate();
            imagePanel.revalidate();
            panel.validate();
            panel.revalidate();

            frame.getContentPane().validate();
            imagePanel.repaint();
            setStatusBarText(" ");

            Dimension screen = Toolkit.getDefaultToolkit().getScreenSize();
            if (size.width <= screen.width || size.height <= screen.height) {
                frame.pack();
            }

            if (currentZoom != 1) {
                frame.setTitle(TITLE + " (" + currentZoom + "x zoom)");
            } else {
                frame.setTitle(TITLE);
            }
        }
    }

    private class DPActionListener implements ActionListener {

        public void actionPerformed(ActionEvent e) {
            if (e.getSource() instanceof Timer) {

                panel.repaint();
                if (shouldDiff() &&
                        System.currentTimeMillis() > createTime + 4 * DELAY) {
                    String expected = System.getProperty(DIFF_PROPERTY);
                    try {
                        String actual = saveToTempFile();
                        DiffImage diff = new DiffImage(expected, actual);
                        diff.frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
                    } catch (IOException ioe) {
                        System.err.println("Error diffing image: " + ioe);
                    }
                    timer.stop();
                } else if (shouldSave() && readyToClose()) {

                    try {
                        if (isAnimated()) {
                            saveAnimated(System.getProperty(SAVE_PROPERTY));
                        } else {
                            save(System.getProperty(SAVE_PROPERTY));
                        }
                    } catch (IOException ioe) {
                        System.err.println("Error saving image: " + ioe);
                    }
                    exit();
                }
            } else if (e.getActionCommand().equals("Exit")) {
                exit();
            } else if (e.getActionCommand().equals("Compare to File...")) {
                compareToFile();
            } else if (e.getActionCommand().equals("Compare to Web File...")) {
                new Thread(DrawingPanel.this::compareToURL).start();
            } else if (e.getActionCommand().equals("Save As...")) {
                saveAs();
            } else if (e.getActionCommand().equals("Save Animated GIF...")) {
                saveAsAnimated();
            } else if (e.getActionCommand().equals("Zoom In")) {
                zoom(currentZoom + 1);
            } else if (e.getActionCommand().equals("Zoom Out")) {
                zoom(currentZoom - 1);
            } else if (e.getActionCommand().equals("Zoom Normal (100%)")) {
                zoom(1);
            } else if (e.getActionCommand().equals("Grid Lines")) {
                setGridLines(((JCheckBoxMenuItem) e.getSource()).isSelected());
            } else if (e.getActionCommand().equals("About...")) {
                JOptionPane.showMessageDialog(frame,
                        ABOUT_MESSAGE,
                        ABOUT_MESSAGE_TITLE,
                        JOptionPane.INFORMATION_MESSAGE);
            }
        }
    }

    private static class DPFileFilter extends FileFilter {
        public boolean accept(File file) {
            return file.isDirectory() ||
                    (file.getName().toLowerCase().endsWith(".png") ||
                            file.getName().toLowerCase().endsWith(".gif"));
        }

        public String getDescription() {
            return "Image files (*.png; *.gif)";
        }
    }

    @FunctionalInterface
    public interface DPMouseEventHandler {
        void onMouseEvent(int x, int y);
    }

    @FunctionalInterface
    public interface DPKeyEventHandler {
        void onKeyEvent(char keyCode);
    }

    private record DPKeyEventHandlerAdapter(DPKeyEventHandler handler, String eventType) implements KeyListener {
        private DPKeyEventHandlerAdapter(DPKeyEventHandler handler, String eventType) {
            this.handler = handler;
            this.eventType = eventType.intern();
        }

        @Override
        public void keyPressed(KeyEvent e) {

        }

        @Override
        public void keyReleased(KeyEvent e) {
            if (eventType.equals("release")) {
                int keyCode = e.getKeyCode();
                if (keyCode < ' ') {
                    return;
                }
                handler.onKeyEvent(e.getKeyChar());
            }
        }

        @Override
        public void keyTyped(KeyEvent e) {
            if (eventType.equals("press")) {
                handler.onKeyEvent(e.getKeyChar());
            }
        }
    }

    private record DPMouseEventHandlerAdapter(DPMouseEventHandler handler,
                                              String eventType) implements MouseInputListener {
            private DPMouseEventHandlerAdapter(DPMouseEventHandler handler, String eventType) {
                this.handler = handler;
                this.eventType = eventType.intern();
            }

            @Override
            public void mousePressed(MouseEvent e) {
                if (eventType.equals("press")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                if (eventType.equals("release")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                if (eventType.equals("click")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseEntered(MouseEvent e) {
                if (eventType.equals("enter")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseExited(MouseEvent e) {
                if (eventType.equals("exit")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseMoved(MouseEvent e) {
                if (eventType.equals("move")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (eventType.equals("drag")) {
                    handler.onMouseEvent(e.getX(), e.getY());
                }
            }
        }

    private class DPMouseListener extends MouseInputAdapter {
        public void mouseMoved(MouseEvent e) {
            int x = e.getX() / currentZoom;
            int y = e.getY() / currentZoom;
            String status = "(x=" + x + ", y=" + y + ")";
            if (x >= 0 && x < width && y >= 0 && y < height) {
                int rgb = getPixelRGB(x, y);
                int r = getRed(rgb);
                int g = getGreen(rgb);
                int b = getBlue(rgb);
                status += ", r=" + r + " g=" + g + " b=" + b;
            }
            setStatusBarText(status);
        }
    }

    private class DPWindowListener extends WindowAdapter {
        public void windowClosing(WindowEvent event) {
            frame.setVisible(false);
            synchronized (LOCK) {
                instances--;
            }
            frame.dispose();
        }
    }

    private class DiffImage extends JPanel
            implements ActionListener, ChangeListener {
        @Serial
        private static final long serialVersionUID = 0;

        private BufferedImage image1;
        private BufferedImage image2;
        private String image1name;
        private int numDiffPixels;
        private int opacity = 50;
        private boolean highlightDiffs = false;

        private Color highlightColor = new Color(224, 0, 224);
        private JLabel image1Label;
        private JLabel image2Label;
        private JLabel diffPixelsLabel;
        private JSlider slider;
        private JCheckBox box;
        private JMenuItem saveAsItem;
        private JMenuItem setImage1Item;
        private JMenuItem setImage2Item;
        private JFrame frame;
        private JButton colorButton;

        public DiffImage(String file1, String file2) throws IOException {
            setImage1(file1);
            setImage2(file2);
            display();
        }

        public void actionPerformed(ActionEvent e) {
            Object source = e.getSource();
            if (source == box) {
                highlightDiffs = box.isSelected();
                repaint();
            } else if (source == colorButton) {
                Color color = JColorChooser.showDialog(frame,
                        "Choose highlight color", highlightColor);
                if (color != null) {
                    highlightColor = color;
                    colorButton.setBackground(color);
                    colorButton.setForeground(color);
                    repaint();
                }
            } else if (source == saveAsItem) {
                saveAs();
            } else if (source == setImage1Item) {
                setImage1();
            } else if (source == setImage2Item) {
                setImage2();
            }
        }

        public void countDiffPixels() {
            if (image1 == null || image2 == null) {
                return;
            }

            int w1 = image1.getWidth();
            int h1 = image1.getHeight();
            int w2 = image2.getWidth();
            int h2 = image2.getHeight();
            int wmax = Math.max(w1, w2);
            int hmax = Math.max(h1, h2);

            numDiffPixels = 0;
            for (int y = 0; y < hmax; y++) {
                for (int x = 0; x < wmax; x++) {
                    int pixel1 = (x < w1 && y < h1) ? image1.getRGB(x, y) : 0;
                    int pixel2 = (x < w2 && y < h2) ? image2.getRGB(x, y) : 0;
                    if (pixel1 != pixel2) {
                        numDiffPixels++;
                    }
                }
            }
        }

        public void display() {
            countDiffPixels();

            setupComponents();
            setupEvents();
            setupLayout();

            frame.pack();
            center(frame);

            frame.setVisible(true);
            toFront(frame);
        }

        public void drawImageFull(Graphics2D g2, BufferedImage image) {
            int iw = image.getWidth();
            int ih = image.getHeight();
            int w = getWidth();
            int h = getHeight();
            int dw = w - iw;
            int dh = h - ih;

            if (dw > 0) {
                g2.fillRect(iw, 0, dw, ih);
            }
            if (dh > 0) {
                g2.fillRect(0, ih, iw, dh);
            }
            if (dw > 0 && dh > 0) {
                g2.fillRect(iw, ih, dw, dh);
            }
            g2.drawImage(image, 0, 0, this);
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;

            if (image1 != null) {
                drawImageFull(g2, image1);
            }

            if (image2 != null) {
                Composite oldComposite = g2.getComposite();
                g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_ATOP, ((float) opacity) / 100));
                drawImageFull(g2, image2);
                g2.setComposite(oldComposite);
            }
            g2.setColor(Color.BLACK);

            if (highlightDiffs && image1 != null && image2 != null) {
                int w1 = image1.getWidth();
                int h1 = image1.getHeight();
                int w2 = image2.getWidth();
                int h2 = image2.getHeight();

                int wmax = Math.max(w1, w2);
                int hmax = Math.max(h1, h2);

                g2.setColor(highlightColor);
                for (int y = 0; y < hmax; y++) {
                    for (int x = 0; x < wmax; x++) {
                        int pixel1 = (x < w1 && y < h1) ? image1.getRGB(x, y) : 0;
                        int pixel2 = (x < w2 && y < h2) ? image2.getRGB(x, y) : 0;
                        if (pixel1 != pixel2) {
                            g2.fillRect(x, y, 1, 1);
                        }
                    }
                }
            }
        }

        public void save(File file) throws IOException {
            String filename = file.getName();
            String extension = filename.substring(filename.lastIndexOf(".") + 1);
            BufferedImage img = new BufferedImage(getPreferredSize().width, getPreferredSize().height, BufferedImage.TYPE_INT_ARGB);
            img.getGraphics().setColor(getBackground());
            img.getGraphics().fillRect(0, 0, img.getWidth(), img.getHeight());
            paintComponent(img.getGraphics());
            ImageIO.write(img, extension, file);
        }

        public void save(String filename) throws IOException {
            save(new File(filename));
        }

        public void saveAs() {
            checkChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();
            try {
                save(selectedFile.toString());
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to save image:\n" + ex);
            }
        }

        public void setImage1() {
            checkChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();
            try {
                setImage1(selectedFile.toString());
                countDiffPixels();
                diffPixelsLabel.setText("(" + numDiffPixels + " pixels differ)");
                image1Label.setText(selectedFile.getName());
                frame.pack();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to set image 1:\n" + ex);
            }
        }

        public void setImage1(BufferedImage image) {
            if (image == null) {
                throw new NullPointerException();
            }

            image1 = image;
            setPreferredSize(new Dimension(
                    Math.max(getPreferredSize().width, image.getWidth()),
                    Math.max(getPreferredSize().height, image.getHeight()))
            );
            if (frame != null) {
                frame.pack();
            }
            repaint();
        }

        public void setImage1(String filename) throws IOException {
            image1name = new File(filename).getName();
            if (filename.startsWith("http")) {
                setImage1(ImageIO.read(new URL(filename)));
            } else {
                setImage1(ImageIO.read(new File(filename)));
            }
        }

        public void setImage2() {
            checkChooser();
            if (chooser.showSaveDialog(frame) != JFileChooser.APPROVE_OPTION) {
                return;
            }

            File selectedFile = chooser.getSelectedFile();
            try {
                setImage2(selectedFile.toString());
                countDiffPixels();
                diffPixelsLabel.setText("(" + numDiffPixels + " pixels differ)");
                image2Label.setText(selectedFile.getName());
                frame.pack();
            } catch (IOException ex) {
                JOptionPane.showMessageDialog(frame, "Unable to set image 2:\n" + ex);
            }
        }

        public void setImage2(BufferedImage image) {
            if (image == null) {
                throw new NullPointerException();
            }

            image2 = image;
            setPreferredSize(new Dimension(
                    Math.max(getPreferredSize().width, image.getWidth()),
                    Math.max(getPreferredSize().height, image.getHeight()))
            );
            if (frame != null) {
                frame.pack();
            }
            repaint();
        }

        public void setImage2(String filename) throws IOException {
            if (filename.startsWith("http")) {
                setImage2(ImageIO.read(new URL(filename)));
            } else {
                setImage2(ImageIO.read(new File(filename)));
            }

        }

        private void setupComponents() {
            String title = "DiffImage";
            if (image1name != null) {
                title = "Compare to " + image1name;
            }
            frame = new JFrame(title);
            frame.setResizable(false);

            slider = new JSlider();
            slider.setPaintLabels(false);
            slider.setPaintTicks(true);
            slider.setSnapToTicks(true);
            slider.setMajorTickSpacing(25);
            slider.setMinorTickSpacing(5);

            box = new JCheckBox("Highlight diffs in color: ", highlightDiffs);

            colorButton = new JButton();
            colorButton.setBackground(highlightColor);
            colorButton.setForeground(highlightColor);
            colorButton.setPreferredSize(new Dimension(24, 24));

            diffPixelsLabel = new JLabel("(" + numDiffPixels + " pixels differ)");
            diffPixelsLabel.setFont(diffPixelsLabel.getFont().deriveFont(Font.BOLD));
            String label1Text = "Expected";
            image1Label = new JLabel(label1Text);
            String label2Text = "Actual";
            image2Label = new JLabel(label2Text);

            setupMenuBar();
        }

        private void setupLayout() {
            JPanel southPanel1 = new JPanel();
            southPanel1.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            southPanel1.add(image1Label);
            southPanel1.add(slider);
            southPanel1.add(image2Label);
            southPanel1.add(Box.createHorizontalStrut(20));

            JPanel southPanel2 = new JPanel();
            southPanel2.setBorder(BorderFactory.createLineBorder(Color.DARK_GRAY));
            southPanel2.add(diffPixelsLabel);
            southPanel2.add(Box.createHorizontalStrut(20));
            southPanel2.add(box);
            southPanel2.add(colorButton);

            Container southPanel = javax.swing.Box.createVerticalBox();
            southPanel.add(southPanel1);
            southPanel.add(southPanel2);

            frame.add(this, BorderLayout.CENTER);
            frame.add(southPanel, BorderLayout.SOUTH);
        }

        private void setupMenuBar() {
            saveAsItem = new JMenuItem("Save As...", 'A');
            saveAsItem.setAccelerator(KeyStroke.getKeyStroke("ctrl S"));
            setImage1Item = new JMenuItem("Set Image 1...", '1');
            setImage1Item.setAccelerator(KeyStroke.getKeyStroke("ctrl 1"));
            setImage2Item = new JMenuItem("Set Image 2...", '2');
            setImage2Item.setAccelerator(KeyStroke.getKeyStroke("ctrl 2"));

            JMenu file = new JMenu("File");
            file.setMnemonic('F');
            file.add(setImage1Item);
            file.add(setImage2Item);
            file.addSeparator();
            file.add(saveAsItem);

            JMenuBar bar = new JMenuBar();
            bar.add(file);

        }

        public void stateChanged(ChangeEvent e) {
            opacity = slider.getValue();
            repaint();
        }

        private void setupEvents() {
            slider.addChangeListener(this);
            box.addActionListener(this);
            colorButton.addActionListener(this);
            saveAsItem.addActionListener(this);
            this.setImage1Item.addActionListener(this);
            this.setImage2Item.addActionListener(this);
        }
    }

    private static class ImageFrame {
        public Image image;
        public int delay;

        public ImageFrame(Image image, int delay) {
            this.image = image;
            this.delay = delay / 10;
        }
    }

    private class ImagePanel extends JPanel {
        @Serial
        private static final long serialVersionUID = 0;
        private Image image;

        public ImagePanel(Image image) {
            super( true);
            setImage(image);
            setBackground(Color.WHITE);
            setPreferredSize(new Dimension(image.getWidth(this), image.getHeight(this)));
            setAlignmentX(0.0f);
        }

        public void paintComponent(Graphics g) {
            super.paintComponent(g);
            Graphics2D g2 = (Graphics2D) g;
            if (currentZoom != 1) {
                g2.scale(currentZoom, currentZoom);
            }
            g2.drawImage(image, 0, 0, this);

            if (gridLines) {
                g2.setPaint(GRID_LINE_COLOR);
                for (int row = 1; row <= getHeight() / gridLinesPxGap; row++) {
                    g2.drawLine(0, row * gridLinesPxGap, getWidth(), row * gridLinesPxGap);
                }
                for (int col = 1; col <= getWidth() / gridLinesPxGap; col++) {
                    g2.drawLine(col * gridLinesPxGap, 0, col * gridLinesPxGap, getHeight());
                }
            }
        }

        public void setImage(Image image) {
            this.image = image;
            repaint();
        }
    }

    static class DirectGif89Frame extends Gif89Frame {

        private final int[] argbPixels;

        public DirectGif89Frame(Image img) throws IOException {
            PixelGrabber pg = new PixelGrabber(img, 0, 0, -1, -1, true);

            String errmsg = null;
            try {
                if (!pg.grabPixels())
                    errmsg = "can't grab pixels from image";
            } catch (InterruptedException e) {
                errmsg = "interrupted grabbing pixels from image";
            }

            if (errmsg != null)
                throw new IOException(errmsg + " (" + getClass().getName()
                        + ")");

            theWidth = pg.getWidth();
            theHeight = pg.getHeight();
            argbPixels = (int[]) pg.getPixels();
            ciPixels = new byte[argbPixels.length];

            img.flush();
        }

        private Object getPixelSource() {
            return argbPixels;
        }
    }

    static class Gif89Encoder {
        private final Dimension dispDim = new Dimension(0, 0);
        private final GifColorTable colorTable;
        private int loopCount = 1;
        private String theComments;
        private final Vector<Gif89Frame> vFrames = new Vector<>();

        public Gif89Encoder() {

            colorTable = new GifColorTable();
        }

        public int getFrameCount() {
            return vFrames.size();
        }

        public Gif89Frame getFrameAt(int index) {
            return isOk(index) ? vFrames.elementAt(index) : null;
        }

        public void addFrame(Gif89Frame gf) throws IOException {
            accommodateFrame(gf);
            vFrames.addElement(gf);
        }

        public void addFrame(Image image) throws IOException {
            DirectGif89Frame frame = new DirectGif89Frame(image);
            addFrame(frame);
        }

        public void setLoopCount(int count) {
            loopCount = count;
        }

        public void encode(OutputStream out) throws IOException {
            int nframes = getFrameCount();
            boolean is_sequence = nframes > 1;

            colorTable.closePixelProcessing();

            putAscii("GIF89a", out);

            writeLogicalScreenDescriptor(out);
            colorTable.encode(out);
            if (is_sequence && loopCount != 1)
                writeNetscapeExtension(out);
            if (theComments != null && !theComments.isEmpty())
                writeCommentExtension(out);

            for (int i = 0; i < nframes; ++i) {
                DirectGif89Frame frame = (DirectGif89Frame) vFrames
                        .elementAt(i);
                frame.encode(out, is_sequence, colorTable.getDepth(),
                        colorTable.getTransparent());
                vFrames.set(i, null);
                System.gc();
            }

            out.write(';');

            out.flush();
        }

        private void accommodateFrame(Gif89Frame gf) throws IOException {
            dispDim.width = Math.max(dispDim.width, gf.getWidth());
            dispDim.height = Math.max(dispDim.height, gf.getHeight());
            colorTable.processPixels(gf);
        }

        private void writeLogicalScreenDescriptor(OutputStream os)
                throws IOException {
            putShort(dispDim.width, os);
            putShort(dispDim.height, os);

            os.write(0xf0 | colorTable.getDepth() - 1);

            int bgIndex = 0;
            os.write(bgIndex);

            os.write(0);
        }

        private void writeNetscapeExtension(OutputStream os) throws IOException {

            os.write('!');
            os.write(0xff);

            os.write(11);
            putAscii("NETSCAPE2.0", os);

            os.write(3);
            os.write(1);

            putShort(loopCount > 1 ? loopCount - 1 : 0, os);

            os.write(0);
        }

        private void writeCommentExtension(OutputStream os) throws IOException {
            os.write('!');
            os.write(0xfe);

            int remainder = theComments.length() % 255;
            int nsubblocks_full = theComments.length() / 255;
            int nsubblocks = nsubblocks_full + (remainder > 0 ? 1 : 0);
            int ibyte = 0;
            for (int isb = 0; isb < nsubblocks; ++isb) {
                int size = isb < nsubblocks_full ? 255 : remainder;

                os.write(size);
                putAscii(theComments.substring(ibyte, ibyte + size), os);
                ibyte += size;
            }

            os.write(0);
        }

        private boolean isOk(int frame_index) {
            return frame_index >= 0 && frame_index < vFrames.size();
        }

    }

    static class GifColorTable {

        private final int[] theColors = new int[256];

        private int colorDepth;
        private int transparentIndex = -1;

        private int ciCount = 0;
        private final ReverseColorMap ciLookup;

        GifColorTable() {
            ciLookup = new ReverseColorMap();
        }

        int getDepth() {
            return colorDepth;
        }

        int getTransparent() {
            return transparentIndex;
        }

        void processPixels(Gif89Frame gf) throws IOException {
            if (gf instanceof DirectGif89Frame)
                filterPixels((DirectGif89Frame) gf);
            else
                trackPixelUsage((IndexGif89Frame) gf);
        }

        void closePixelProcessing()
        {
            colorDepth = computeColorDepth(ciCount);
        }

        void encode(OutputStream os) throws IOException {
            int palette_size = 1 << colorDepth;
            for (int i = 0; i < palette_size; ++i) {
                os.write(theColors[i] >> 16 & 0xff);
                os.write(theColors[i] >> 8 & 0xff);
                os.write(theColors[i] & 0xff);
            }
        }

        private void filterPixels(DirectGif89Frame dgf) throws IOException {

            int[] argb_pixels = (int[]) dgf.getPixelSource();
            byte[] ci_pixels = dgf.getPixelSink();
            int npixels = argb_pixels.length;
            for (int i = 0; i < npixels; ++i) {
                int argb = argb_pixels[i];

                if ((argb >>> 24) < 0x80)
                    if (transparentIndex == -1)
                        transparentIndex = ciCount;
                    else if (argb != theColors[transparentIndex]) {
                        ci_pixels[i] = (byte) transparentIndex;
                        continue;
                    }

                int color_index = ciLookup.getPaletteIndex(argb & 0xffffff);

                if (color_index == -1) {
                    if (ciCount == 256)
                        throw new IOException(
                                "can't encode as GIF (> 256 colors)");

                    theColors[ciCount] = argb;

                    ciLookup.put(argb & 0xffffff, ciCount);

                    ci_pixels[i] = (byte) ciCount;

                    ++ciCount;
                } else
                    ci_pixels[i] = (byte) color_index;
            }
        }

        private void trackPixelUsage(IndexGif89Frame igf) {
            byte[] ci_pixels = (byte[]) igf.getPixelSource();
            for (byte ciPixel : ci_pixels)
                if (ciPixel >= ciCount)
                    ciCount = ciPixel + 1;
        }

        private int computeColorDepth(int colorcount) {
            if (colorcount <= 2)
                return 1;
            if (colorcount <= 4)
                return 2;
            if (colorcount <= 16)
                return 4;
            return 8;
        }
    }

    static class ReverseColorMap {

        private static class ColorRecord {
            int rgb;
            int ipalette;

            ColorRecord(int rgb, int ipalette) {
                this.rgb = rgb;
                this.ipalette = ipalette;
            }
        }

        private static final int HCAPACITY = 2053;

        private final ColorRecord[] hTable = new ColorRecord[HCAPACITY];

        int getPaletteIndex(int rgb) {
            ColorRecord rec;

            for (int itable = rgb % hTable.length; (rec = hTable[itable]) != null
                    && rec.rgb != rgb; itable = ++itable % hTable.length)
                ;

            if (rec != null)
                return rec.ipalette;

            return -1;
        }

        void put(int rgb, int ipalette) {
            int itable;

            for (itable = rgb % hTable.length; hTable[itable] != null; itable = ++itable % hTable.length)
                ;

            hTable[itable] = new ColorRecord(rgb, ipalette);
        }
    }

    abstract static class Gif89Frame {

        public static final int DM_LEAVE = 1;

        int theWidth = -1;
        int theHeight = -1;
        byte[] ciPixels;

        private final Point
                thePosition = new Point(0, 0);
        private boolean isInterlaced;
        private int csecsDelay;

        public void setDelay(int interval) {
            csecsDelay = interval;
        }

        Gif89Frame() {

        }

        int getWidth() {
            return theWidth;
        }

        int getHeight() {
            return theHeight;
        }

        byte[] getPixelSink() {
            return ciPixels;
        }

        void encode(OutputStream os, boolean epluribus, int color_depth, int transparent_index) throws IOException {
            writeGraphicControlExtension(os, epluribus, transparent_index);
            writeImageDescriptor(os);
            new GifPixelsEncoder(theWidth, theHeight, ciPixels, isInterlaced, color_depth).encode(os);
        }

        private void writeGraphicControlExtension(OutputStream os, boolean epluribus, int itransparent) throws IOException {
            int transflag = itransparent == -1 ? 0 : 1;
            if (transflag == 1 || epluribus) {
                os.write('!');
                os.write(0xf9);
                os.write(4);
                os.write((DM_LEAVE << 2) | transflag);
                putShort(csecsDelay, os);
                os.write(itransparent);
                os.write(0);
            }
        }

        private void writeImageDescriptor(OutputStream os) throws IOException {
            os.write(',');
            putShort(thePosition.x, os);
            putShort(thePosition.y, os);
            putShort(theWidth, os);
            putShort(theHeight, os);
            os.write(isInterlaced ? 0x40 : 0);
        }

    }

    static class GifPixelsEncoder {

        private static final int EOF = -1;

        private final int imgW;
        private final int imgH;
        private final byte[] pixAry;
        private final boolean wantInterlaced;
        private final int initCodeSize;

        private int countDown;
        private int xCur, yCur;
        private int curPass;

        GifPixelsEncoder(int width, int height, byte[] pixels,
                         boolean interlaced, int color_depth) {
            imgW = width;
            imgH = height;
            pixAry = pixels;
            wantInterlaced = interlaced;
            initCodeSize = Math.max(2, color_depth);
        }

        void encode(OutputStream os) throws IOException {
            os.write(initCodeSize);

            countDown = imgW * imgH;
            xCur = yCur = curPass = 0;

            compress(initCodeSize + 1, os);

            os.write(0);
        }

        private void bumpPosition() {
            ++xCur;

            if (xCur == imgW) {
                xCur = 0;

                if (!wantInterlaced)
                    ++yCur;
                else
                    switch (curPass) {
                        case 0:
                            yCur += 8;
                            if (yCur >= imgH) {
                                ++curPass;
                                yCur = 4;
                            }
                            break;
                        case 1:
                            yCur += 8;
                            if (yCur >= imgH) {
                                ++curPass;
                                yCur = 2;
                            }
                            break;
                        case 2:
                            yCur += 4;
                            if (yCur >= imgH) {
                                ++curPass;
                                yCur = 1;
                            }
                            break;
                        case 3:
                            yCur += 2;
                            break;
                    }
            }
        }

        private int nextPixel() {
            if (countDown == 0)
                return EOF;

            --countDown;

            byte pix = pixAry[yCur * imgW + xCur];

            bumpPosition();

            return pix & 0xff;
        }

        static final int BITS = 12;

        static final int HSIZE = 5003;

        int n_bits;
        int maxbits = BITS;
        int maxcode;
        int maxmaxcode = 1 << BITS;

        final int MAXCODE(int n_bits) {
            return (1 << n_bits) - 1;
        }

        int[] htab = new int[HSIZE];
        int[] codetab = new int[HSIZE];

        int hsize = HSIZE;
        int free_ent = 0;

        boolean clear_flg = false;

        int g_init_bits;

        int ClearCode;
        int EOFCode;

        void compress(int init_bits, OutputStream outs) throws IOException {
            int fcode;
            int i;
            int c;
            int ent;
            int disp;
            int hsize_reg;
            int hshift;

            g_init_bits = init_bits;

            clear_flg = false;
            n_bits = g_init_bits;
            maxcode = MAXCODE(n_bits);

            ClearCode = 1 << (init_bits - 1);
            EOFCode = ClearCode + 1;
            free_ent = ClearCode + 2;

            char_init();

            ent = nextPixel();

            hshift = 0;
            for (fcode = hsize; fcode < 65536; fcode *= 2)
                ++hshift;
            hshift = 8 - hshift;

            hsize_reg = hsize;
            cl_hash(hsize_reg);

            output(ClearCode, outs);

            outer_loop: while ((c = nextPixel()) != EOF) {
                fcode = (c << maxbits) + ent;
                i = (c << hshift) ^ ent;

                if (htab[i] == fcode) {
                    ent = codetab[i];
                    continue;
                } else if (htab[i] >= 0) {
                    disp = hsize_reg - i;
                    if (i == 0)
                        disp = 1;
                    do {
                        if ((i -= disp) < 0)
                            i += hsize_reg;

                        if (htab[i] == fcode) {
                            ent = codetab[i];
                            continue outer_loop;
                        }
                    } while (htab[i] >= 0);
                }
                output(ent, outs);
                ent = c;
                if (free_ent < maxmaxcode) {
                    codetab[i] = free_ent++;
                    htab[i] = fcode;
                } else
                    cl_block(outs);
            }
            output(ent, outs);
            output(EOFCode, outs);
        }

        int cur_accum = 0;
        int cur_bits = 0;

        int[] masks = { 0x0000, 0x0001, 0x0003, 0x0007, 0x000F, 0x001F, 0x003F,
                0x007F, 0x00FF, 0x01FF, 0x03FF, 0x07FF, 0x0FFF, 0x1FFF, 0x3FFF,
                0x7FFF, 0xFFFF };

        void output(int code, OutputStream outs) throws IOException {
            cur_accum &= masks[cur_bits];

            if (cur_bits > 0)
                cur_accum |= (code << cur_bits);
            else
                cur_accum = code;

            cur_bits += n_bits;

            while (cur_bits >= 8) {
                char_out((byte) (cur_accum & 0xff), outs);
                cur_accum >>= 8;
                cur_bits -= 8;
            }

            if (free_ent > maxcode || clear_flg) {
                if (clear_flg) {
                    maxcode = MAXCODE(n_bits = g_init_bits);
                    clear_flg = false;
                } else {
                    ++n_bits;
                    if (n_bits == maxbits)
                        maxcode = maxmaxcode;
                    else
                        maxcode = MAXCODE(n_bits);
                }
            }

            if (code == EOFCode) {
                while (cur_bits > 0) {
                    char_out((byte) (cur_accum & 0xff), outs);
                    cur_accum >>= 8;
                    cur_bits -= 8;
                }

                flush_char(outs);
            }
        }

        void cl_block(OutputStream outs) throws IOException {
            cl_hash(hsize);
            free_ent = ClearCode + 2;
            clear_flg = true;

            output(ClearCode, outs);
        }

        void cl_hash(int hsize) {
            for (int i = 0; i < hsize; ++i)
                htab[i] = -1;
        }

        int a_count;

        void char_init() {
            a_count = 0;
        }

        byte[] accum = new byte[256];

        void char_out(byte c, OutputStream outs) throws IOException {
            accum[a_count++] = c;
            if (a_count >= 254)
                flush_char(outs);
        }

        void flush_char(OutputStream outs) throws IOException {
            if (a_count > 0) {
                outs.write(a_count);
                outs.write(accum, 0, a_count);
                a_count = 0;
            }
        }
    }

    static class IndexGif89Frame extends Gif89Frame {
        public IndexGif89Frame(int width, int height, byte[] ci_pixels) {
            theWidth = width;
            theHeight = height;
            ciPixels = new byte[theWidth * theHeight];
            System.arraycopy(ci_pixels, 0, ciPixels, 0, ciPixels.length);
        }

        private Object getPixelSource() {
            return ciPixels;
        }
    }

    private static void putAscii(String s, OutputStream os) throws IOException {
        byte[] bytes = new byte[s.length()];
        for (int i = 0; i < bytes.length; ++i) {
            bytes[i] = (byte) s.charAt(i);
        }
        os.write(bytes);
    }

    private static void putShort(int i16, OutputStream os) throws IOException {
        os.write(i16 & 0xff);
        os.write(i16 >> 8 & 0xff);
    }
}