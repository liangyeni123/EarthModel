/**
 * Yeni Liang
 * Student number: #201573656 **/

import java.awt.Button;
import java.awt.Frame;
import java.awt.Panel;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.zip.ZipEntry;
import java.awt.Canvas;
import java.awt.Color;
import java.awt.Graphics;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.awt.event.KeyAdapter;
import java.awt.event.KeyEvent;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseMotionListener;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;

import javax.imageio.ImageIO;
import org.w3c.dom.css.RGBColor;


// Main class
public class ModelEarth extends Frame implements ActionListener {
	View3DCanvas viewer;
	// Constructor
	public ModelEarth(Vertex center, double radius) {
		super("Earth Model");
		Panel controls = new Panel();
		Button button = new Button("More Detail");
		button.addActionListener(this);
		controls.add(button);
		button = new Button("Less Detail");
		button.addActionListener(this);
		controls.add(button);
		button = new Button("Displacement");
		button.addActionListener(this);
		controls.add(button);
		button = new Button("Culling");
		button.addActionListener(this);
		controls.add(button);
		button = new Button("Render");
		button.addActionListener(this);
		controls.add(button);
		// add canvas and control panel
		viewer = new View3DCanvas(new Sphere(center, radius));
		add("Center", viewer);
		add("South", controls);
		addWindowListener(new ExitListener());
	}
	class ExitListener extends WindowAdapter {
		public void windowClosing(WindowEvent e) {
			System.exit(0);
		}
	}
	// Action listener for buttons
	public void actionPerformed(ActionEvent e) {
		
		if ( ((Button)e.getSource()).getLabel().equals("More Detail") ){
			viewer.level ++;
			viewer.sawtooth();
		}
		else if ( ((Button)e.getSource()).getLabel().equals("Less Detail") ){
			viewer.level -= viewer.level > 0 ? 1 : 0;
		    viewer.sawtooth();
		}
		else if ( ((Button)e.getSource()).getLabel().equals("Displacement") ){
			viewer.displace = ! viewer.displace;
			viewer.loadelevationmap("earth_elevation.png");
			viewer.loadearthmap("earthmap.png");
		}
		else if ( ((Button)e.getSource()).getLabel().equals("Culling") ){
			viewer.culling = ! viewer.culling;
			viewer.loadearthmap("earthmap.png");
		}
		else if ( ((Button)e.getSource()).getLabel().equals("Render") ){
			viewer.render = ! viewer.render;
			viewer.displace = ! viewer.displace;
			viewer.culling = ! viewer.culling;
			viewer.loadelevationmap("earth_elevation.png");
			viewer.loadearthmap("earthmap.png");
			viewer.loadnightlightmap("nightlightmap.png");
		}	
		viewer.repaint();
	}
	
	public static void main(String[] args) {
		Vertex center = new Vertex(0, 0, 0);
		double radius = 2;
		if ( args.length >= 3 )
			center = new Vertex(Double.parseDouble(args[0]), Double.parseDouble(args[1]), Double.parseDouble(args[2]));
		if ( args.length == 4 )
			radius = Double.parseDouble(args[3]);
		ModelEarth window = new ModelEarth(center, radius);
		window.setSize(700, 600);
		window.setBackground(Color.BLACK);
		window.setVisible(true);
	} 
}


class View3DCanvas extends Canvas {
	// the sphere to display.
	Sphere sphere;
	// pitch and paw angles for world to camera transform
	int pitch, yaw =0;
	// focal length and image size for camera to screen transform
	int focal, size;
	// canvas size for screen to raster transform
	int width, height, scale;
	// tessellation level
	// this will also give the initial value of level = 0
	int level;
	// whether displacement map or back face culling is enabled
	boolean displace, culling,render;
	
	PhongModel light;
	
	private BufferedImage image = null;
	private BufferedImage image2 = null;
	private BufferedImage image3 = null;
	
	// initialize the 3D view canvas
	public View3DCanvas(Sphere s) {
		sphere = s;
		focal = 5; size = 3;
		DragListener drag = new DragListener();
		addMouseListener(drag);
		addMouseMotionListener(drag);
		addKeyListener(new ArrowListener());
		addComponentListener(new ResizeListener());
	}
	// Demo transform between world and camera coordinates
	public Vertex world2Camera(Vertex from) {
		return rotate(from);
	}
	// Demo transform between camera and screen coordinates (scaled parallel projection)
	public Vertex camera2Screen(Vertex from) {
		return new Vertex((from.x * (double) focal) / ((from.z + (double) focal) * (double) size),
				(from.y * (double) focal) / ((from.z + (double) focal) * (double) size), focal);
	}
	// Transformation: rotation
	private Vertex rotate(Vertex from){
		double ryaw = -((double)yaw / 180 * Math.PI);
		double rpitch = -((double)pitch / 180 * Math.PI);
		
		double[][] arry = {
				{Math.cos(ryaw), 0, Math.sin(ryaw)},
				{0, 1, 0},
				{-Math.sin(ryaw), 0, Math.cos(ryaw)}
		};
		
		double[][] arrx = {
				{1, 0, 0},
				{0, Math.cos(rpitch), -Math.sin(rpitch)},
				{0, Math.sin(rpitch), Math.cos(rpitch)}
			};
		
		return new Matrix(arrx).multiple(new Matrix(arry).multiple(from));
	}
	
	// Transform between screen and raster coordinates
	public Vertex screen2Raster(Vertex from) {
		return new Vertex(from.x*scale+width/2, -from.y*scale+height/2, 0);
	}
	public void paint(Graphics g) {
		// display current parameter values
		g.setColor(Color.blue);
		g.drawString("Pitch = "+pitch+", Yaw = "+yaw+", Focal = "+focal+", Size = "+size, 10, 20);
		g.drawString("Tessellation level : "+level, 10, 40);
		g.drawString("Displacement : "+(displace?"on":"off"), 10, 60);
		g.drawString("Culling : "+(culling?"on":"off"), 10, 80);
		// draw the triangle mesh obtained through sphere tessellation
		g.setColor(Color.green);
		//System.out.println(displace);
		sphere.setDisplaced(displace);
		
		for ( Triangle t : sphere.triangles ) {
			Vertex p0 = null, p1 = null, p2 = null;
			p0 = sphere.vertices[t.v0];
			p1 = sphere.vertices[t.v1];
			p2 = sphere.vertices[t.v2];
			Vertex tmp = p0;
			double x;
			
			if(displace == true){
				p0 = displace(p0);
				p1 = displace(p1);
				p2 = displace(p2);
			}
			
			p0 = world2Camera(p0);
			p1 = world2Camera(p1);
			p2 = world2Camera(p2);
			
			if((culling == false) || (isCulled(p0, p1, p2) == false)){
				p0 = screen2Raster(camera2Screen(p0));
				p1 = screen2Raster(camera2Screen(p1));
				p2 = screen2Raster(camera2Screen(p2));
				
				int[] tmpA = {(int)p0.x,(int)p1.x,(int)p2.x};
				int[] tmpB = {(int)p0.y,(int)p1.y,(int)p2.y};
				/*you must put this if, because when the viewer is initialized, it will paint the 
				 * canvas for the first time without load image2 which will throw an exception*/
				if(render){
//					x = tmp.dot(light.directionalLight(tmp));
					Color lightc = PhongModel.PhongModel(tmp, tmp.norm().multiple(1), returnColor(tmp));
					if(lightc.getRed()<5&&lightc.getGreen()<5&&lightc.getBlue()<5){
					lightc = returnNightColor(tmp);
					} 
					g.setColor(lightc);
				}
				//	g.setColor(PhongModel.PhongModel(tmp, tmp.norm(), Color.WHITE));
					
				//g.drawPolygon(tmpA, tmpB, 3);
				g.drawLine((int)p0.x, (int)p0.y, (int)p1.x, (int)p1.y);
				g.drawLine((int)p1.x, (int)p1.y, (int)p2.x, (int)p2.y);
				g.drawLine((int)p2.x, (int)p2.y, (int)p0.x, (int)p0.y);
			}
		}
	}
	
	private boolean isCulled(Vertex v0, Vertex v1, Vertex v2){
		Vertex P = new Vertex(0, 0, -focal);
		Vertex N = new Vertex((double)(v0.x + v1.x + v2.x) / 3,
				(double)(v0.y + v1.y + v2.y) / 3,
				(double)(v0.z + v1.z + v2.z) / 3
				);
		Vertex V = N.minus(P);
		double d = V.dot(N);
		
		if(d >= 0)
			return false;
		return true;
	}
	
	public void loadelevationmap(String filename){
		try{
			image = ImageIO.read(new File(filename));
		}
		catch(IOException e){
			System.out.println("Unable to open file");
		}
	}
	//this is to load earth image with color
	public void loadearthmap(String filename){
		try{
			image2 = ImageIO.read(new File(filename));
		}
		catch(IOException e){
			System.out.println("Unable to open file");
		}
	}
	
	public void loadnightlightmap(String filename){
		try{
			image3 = ImageIO.read(new File(filename));
		}
		catch(IOException e){
			System.out.println("Unable to open file");
		}
	}

	
	private Vertex displace(Vertex from){
		double theta = Math.acos(from.y / sphere.radius) * 180 / Math.PI;
		double psi = Math.acos(from.x / Math.sqrt(from.x * from.x + from.z * from.z)) * 180 / Math.PI;
		if(from.z < 0)
			psi = 360 - psi;
		int y_img = (int)(theta * image.getHeight()) / 180;
		int x_img = (int)(psi * image.getWidth()) / 360;
		/*if ">=" true then return x_img - img.getwidth; or return x_img*/
		x_img = x_img >= image.getWidth() ? x_img - image.getWidth() : x_img;
		y_img = y_img >= image.getHeight() ? y_img - image.getHeight() : y_img;
		
		int attitude = new Color(image.getRGB(x_img, y_img)).getRed();

		double newR = sphere.radius * (1 + 1.0 * attitude / 255 / 8);
		return new Vertex(newR * from.x / sphere.radius + sphere.center.x,
				newR * from.y / sphere.radius + sphere.center.x,
				newR * from.z / sphere.radius + sphere.center.x
				);
	}
	/*this is to sample from colorful earth map*/
	private Color returnColor(Vertex frm){
		Vertex v1 = frm.tocartesianCoordinate(image2.getHeight(), image2.getWidth(), sphere.radius);
		Color tmp = new Color(image2.getRGB((int)v1.x, (int)v1.y));
		
		return tmp;
	}
	
	private Color returnNightColor(Vertex frm){
		Vertex v1 = frm.tocartesianCoordinate(image3.getHeight(), image3.getWidth(), sphere.radius);
		Color tmp = new Color(image3.getRGB((int)v1.x, (int)v1.y));
		
		return tmp;
	}
	
	
	public void sawtooth(){
		sphere.sawtooth(level);
	}

	// Resize listener for updating canvas size
	class ResizeListener extends ComponentAdapter {
		public void componentResized(ComponentEvent e) {
			width = getWidth();
			height = getHeight();
			scale = Math.min(width/2, height/2);
		}
	}
	// Action listener for mouse
	class DragListener extends MouseAdapter implements MouseMotionListener {
		int lastX, lastY;
		
		public void mousePressed(MouseEvent e) {
			lastX = e.getX();
			lastY = e.getY();
		}
		// update pitch and yaw angles when the mouse is dragged.
		public void mouseDragged(MouseEvent e) {
			yaw -= e.getX() - lastX;
			pitch -= e.getY() - lastY;
			PhongModel.yaw = yaw;
			PhongModel.pitch = pitch;
			lastX = e.getX();
			lastY = e.getY();
			repaint();
		}
	}
	// Action listener for keyboard
	class ArrowListener extends KeyAdapter {
		public void keyPressed(KeyEvent e) {
			if ( e.getKeyCode() == KeyEvent.VK_DOWN && focal>3 )
				focal --;
			else if ( e.getKeyCode() == KeyEvent.VK_UP && focal<20 )
				focal ++;
			else if ( e.getKeyCode() == KeyEvent.VK_LEFT && size>1 )
				size --;
			else if ( e.getKeyCode() == KeyEvent.VK_RIGHT && size<20 )
				size ++;
			repaint();
		}
	}
}


//Vertex definition: define multi method for Vertex as vector;coordinates for a 3D point;
class Vertex {
	double x, y, z;    //coordination
	double u,v;        //2D texture
	
	//constructors
	public Vertex(double x, double y, double z) {
		this.x = x; this.y = y; this.z = z;
	}
	
	public double length(){
		return Math.sqrt(x * x + y * y + z * z);
	}
	
	public Vertex norm(){
		double length = length();
		return new Vertex(x / length, y / length, z / length);
	}
	
	public Vertex add(Vertex b){
		return new Vertex(x + b.x, y + b.y, z + b.z);
	}
	
	public Vertex multiple(double b){
		return new Vertex(x * b, y * b, z * b);
	}
	
	public Vertex minus(Vertex b){
		return new Vertex(x - b.x, y - b.y, z - b.z);
	}
	
	public Vertex cross(Vertex b){
		return new Vertex(y * b.z - z * b.y,
				z * b.x - x * b.z,
				x * b.y - y * b.x
				);
	}
	
	public double dot(Vertex b){
		return x * b.x + y * b.y + z * b.z;
	}
	
	public Vertex tocartesianCoordinate(int height,int width,double sphereRadius){
		double theta = Math.acos(y / sphereRadius) * 180 / Math.PI;
		double psi = Math.acos(x / Math.sqrt(x * x + z * z)) * 180 / Math.PI;
		if(z < 0)
			psi = 360 - psi;
		int y_img = (int)(theta * height) / 180;
		int x_img = (int)(psi * width) / 360;
		/*if ">=" true then return x_img - img.getwidth; or return x_img*/
		x_img = x_img >= width ? x_img - width : x_img;
		y_img = y_img >= height ? y_img - height : y_img;
		Vertex tmp = new Vertex(x_img, y_img, 0);
		return tmp;
	}
	
	public String toString(){
		return String.format("Vertex(%f, %f, %f)", x, y, z);
	}
}

class Triangle {
	public int v0, v1, v2;
	public double a, b, c;
	public Triangle(int u, int v, int w) {
		v0 = u; v1 = v; v2 = w;
	}
	
	
class Point3D{
	public double x;
	public double y;
	public double z;
	
	
	public Point3D(double x, double y, double z) {
		this.x = x;
		this.y = y;
		this.z = z;
	}
}

}

class Sphere {
	double radius;
	Vertex center;
	public Vertex[] vertices;
	public Triangle[] triangles;
	
	Vertex[] init_vertices;
	Triangle[] init_triangles;
	
	private boolean isDisplaced = false;

	public Sphere(Vertex c, double r) {
		center = c;
		radius = r;
		
		// Generate 6 point as the initial status
		// Generate triangle mesh for Octahedron - start point for tessellation
		vertices = new Vertex[6];
		vertices[0] = new Vertex(center.x, center.y, center.z+radius);
		vertices[1] = new Vertex(center.x+radius, center.y, center.z);
		vertices[2] = new Vertex(center.x, center.y+radius, center.z);
		vertices[3] = new Vertex(center.x-radius, center.y, center.z);
		vertices[4] = new Vertex(center.x, center.y-radius, center.z);
		vertices[5] = new Vertex(center.x, center.y, center.z-radius);
		
		triangles = new Triangle[8];
		triangles[0] = new Triangle(0, 1, 2);
		triangles[1] = new Triangle(0, 2, 3);
		triangles[2] = new Triangle(0, 3, 4);
		triangles[3] = new Triangle(0, 4, 1);
		triangles[4] = new Triangle(5, 2, 1);
		triangles[5] = new Triangle(5, 3, 2);
		triangles[6] = new Triangle(5, 4, 3);
		triangles[7] = new Triangle(5, 1, 4);
		
		init_vertices = vertices.clone();
		init_triangles = triangles.clone();
	}
	
	private void sawtooth(){
		/*Here, current vertices is put into vertices2 used to generate new triangles;
		 * triangles2 is initialized with nothing;*/
		List<Vertex> vertices2 = new ArrayList<Vertex>(Arrays.asList(vertices));
		List<Triangle> triangles2 = new ArrayList<Triangle>();
		/*Every iteration, saved triangles will be used to find the vertices for new triangles
		 * which are generated based on old triangles; that's why we should save both vertices and 
		 * triangles info.*/
		for(Triangle t: triangles){
			Vertex temp = null;
			int nv01 = vertices2.size();
			temp = vertices2.get(t.v0).add(vertices2.get(t.v1)).norm().multiple(radius);
			vertices2.add(temp);
			int nv12 = vertices2.size();
			temp = vertices2.get(t.v1).add(vertices2.get(t.v2)).norm().multiple(radius);
			vertices2.add(temp);
			int nv20 = vertices2.size();
			temp = vertices2.get(t.v2).add(vertices2.get(t.v0)).norm().multiple(radius);
			vertices2.add(temp);
			/*Every new triangles will be saved to triangles2*/
			triangles2.add(new Triangle(nv01, nv12, nv20));
			triangles2.add(new Triangle(t.v0, nv01, nv20));
			triangles2.add(new Triangle(t.v1, nv01, nv12));
			triangles2.add(new Triangle(t.v2, nv12, nv20));
		}

		triangles = triangles2.toArray(triangles);
		vertices = vertices2.toArray(vertices);
	}
	/*this is to generate triangle mesh*/
	public void sawtooth(int n){
		triangles = init_triangles.clone();
		vertices = init_vertices.clone();
		
		for(int i = 0; i < n; i++)
			sawtooth();
	}

	public boolean isDisplaced() {
		return isDisplaced;
	}

	public void setDisplaced(boolean isDisplaced) {
		this.isDisplaced = isDisplaced;
	}
}

class Matrix {
	double data[][] = null;
	int cols = 0;
	int rows = 0;
	
	public Matrix(int cols, int rows){
		this.cols = cols;
		this.rows = rows;
		data = new double[rows][cols];
	}
	
	public Matrix(double[][] arr){
		this.rows = arr.length;
		this.cols = arr[0].length;
		data = arr.clone();
	}
	
	public void fromArray(double [][] arr){
		data = arr.clone();
	}
	
	/*this is for what? is there some error if it's for the multiplication*/
	public Matrix matrix(Matrix b){
		assert((cols == b.rows) && (rows == b.cols));
		
		Matrix ret = new Matrix(rows, b.cols);
		for(int i = 0; i < rows; i++){
			for(int j = 0; j < b.cols; j++){
				double result = 0;
				for(int m = 0; m < cols; m++){
					result += data[i][m] * b.data[j][m]; 
				}
				ret.data[i][j] = result;
			}
		}
		
		return ret;
	}
	
	public Vertex multiple(Vertex b) {
		if(rows == 4){
			double x = b.x * data[0][0] + b.y * data[0][1] + b.z * data[0][2] + 1 * data[0][3];
			double y = b.x * data[1][0] + b.y * data[1][1] + b.z * data[1][2] + 1 * data[1][3];
			double z = b.x * data[2][0] + b.y * data[2][1] + b.z * data[2][2] + 1 * data[2][3];
			double w = b.x * data[3][0] + b.y * data[3][1] + b.z * data[3][2] + 1 * data[3][3];
			return new Vertex(x / w, y / w, z / w);
		}
		if(rows == 3){
			double x = b.x * data[0][0] + b.y * data[0][1] + b.z * data[0][2];
			double y = b.x * data[1][0] + b.y * data[1][1] + b.z * data[1][2];
			double z = b.x * data[2][0] + b.y * data[2][1] + b.z * data[2][2];
			return new Vertex(x, y, z);
		}
		
		throw new RuntimeException("matrix multiple vertex");
	}
}

 class RGB{
	double r,g,b;
	public RGB(double r,double g,double b){
		this.r = r;
		this.g = g;
		this.b = b;
	}
	public RGB(int rgb){
		int r = rgb >> 16 & 0xff;
		int g = rgb >> 8 & 0xff;
		int b = rgb >> 0 & 0xff;

	}
	public void scale(double scale){
		r *= scale;
		g *= scale;
		b *= scale;
	}
	public void add(RGB texel){
		r += texel.r;
		g += texel.g;
		b += texel.b;
	}
	public int toRGB(){
		return 0xff000000 | (int) (r * 255.99) << 16 | (int) (g * 255.99)  << 8 | 
				(int) (b * 255.99) << 0;
	} 
} 
