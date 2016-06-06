import java.awt.Color;

public class PhongModel {
	
	class SimpleDirLight {
		Vertex light;
		public SimpleDirLight(double x, double y, double z){
			light = new Vertex(x, y, z);
			light = light.norm();
		}
		public Vertex DirectionalLight(Vertex from){
			return light;
		}
	}

	static Vertex source = new Vertex(2,2,7);
	static Vertex intensity = new Vertex(4, 4, 4);
	static int n = 40;
	static double kd = 0.4;
	static double ks = 0.01;
	static double c1 = 1;
	static double c2 = 0;
	static double c3 = 0.01;
	static double yaw, pitch, size;
	
	static public Vertex intensity(Vertex point) {
		Vertex d = source.norm();
		double angleOfLight = Math.max(0, d.dot(point));
		return new Vertex(angleOfLight * intensity.x, angleOfLight * intensity.y, angleOfLight * intensity.z);
	}
	static public Vertex reflectVector(Vertex a, Vertex b) {
		double norm = Math.sqrt(b.x * b.x + b.y * b.y + b.z * b.z);     //length b
		Vertex u = new Vertex(b.x / norm, b.y / norm, b.z / norm);   	//normalize b
		double t = a.x * u.x + a.y * u.y + a.z * u.z;					// dot product
		if(t<=0){
			return new Vertex(0, 0, 0);
		}
		//scale norm-b by dot product
		Vertex d = new Vertex(t * u.x, t * u.y, t * u.z);
		return new Vertex(2 * d.x - a.x, 2 * d.y - a.y, 2 * d.z - a.z);
	}

	static public Vertex view(Vertex P) {
		Vertex v = new Vertex(0, 0, -5);
		return new Vertex(v.x - P.x, v.y - P.y, v.z - P.z);
	}

	static public Color PhongModel(Vertex P, Vertex N, Color c) {
		N = N.norm();
		Vertex L = inverse(source).norm();
		Vertex R = reflectVector(L, N);
		R = R.norm();
		Vertex I = intensity;
		Vertex V = view(P);
		V = V.norm();
		double diffuse = kd * (N.x * L.x + N.y * L.y + N.z * L.z);
		Color dcolor = new Color(clamp(I.x * c.getRed() * diffuse), clamp(I.y * c.getGreen() * diffuse),
				clamp(I.z * c.getBlue() * diffuse));
		double specular = ks * Math.pow((V.x * R.x + V.y * R.y + V.z * R.z),n);
		Color s = c;
		Color scolor = new Color(clamp(I.x * s.getRed() * specular), clamp(I.y * s.getGreen() * specular),
				clamp(I.z * s.getBlue() * specular));
		
		return new Color(clamp(dcolor.getRed() + scolor.getRed()), clamp(dcolor.getGreen() + scolor.getGreen()),
				clamp(dcolor.getBlue() + scolor.getBlue()));
	}
	
	static private Vertex inverse(Vertex from){
		double ryaw = ((double)yaw / 180 * Math.PI);
		double rpitch = ((double)pitch / 180 * Math.PI);
		
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
		
		Vertex newV = new Matrix(arry).multiple(new Matrix(arrx).multiple(from));
		return newV;
	}

	static public int clamp(double value) {
		int v = (int) value;
		v = Math.min(v, 255);
		return Math.max(v, 0);
	}
	

}
