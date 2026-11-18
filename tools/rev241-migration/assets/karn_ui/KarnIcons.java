import java.awt.*;
import java.awt.geom.*;

/**
 * The Karn icon set (2026-11-12): solid silhouettes on a 24 x 24 grid with their inner details cut out (Area
 * subtraction), so they read as clean single-colour glyphs at 12-24 px. KarnArt.icon() paints them with a gradient and a
 * dark drop shadow, at 4x, box-filtered down.
 */
public final class KarnIcons {

	private KarnIcons() {
	}

	public static Shape detail(String name) {
		return null; // details are cut out of the silhouettes
	}

	static Area a(Shape s) {
		return new Area(s);
	}

	static Area rr(double x, double y, double w, double h, double r) {
		return a(new RoundRectangle2D.Double(x, y, w, h, r, r));
	}

	static Area rect(double x, double y, double w, double h) {
		return a(new Rectangle2D.Double(x, y, w, h));
	}

	static Area circle(double cx, double cy, double r) {
		return a(new Ellipse2D.Double(cx - r, cy - r, r * 2, r * 2));
	}

	static Area poly(double... xy) {
		Path2D p = new Path2D.Double();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2)
			p.lineTo(xy[i], xy[i + 1]);
		p.closePath();
		return a(p);
	}

	static Area stroke(Shape s, double w) {
		return a(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND).createStrokedShape(s));
	}

	static Area line(double x0, double y0, double x1, double y1, double w) {
		return stroke(new Line2D.Double(x0, y0, x1, y1), w);
	}

	static Area rot(Area s, double deg, double cx, double cy) {
		return s.createTransformedArea(AffineTransform.getRotateInstance(Math.toRadians(deg), cx, cy));
	}

	static Area minus(Area a, Area... cuts) {
		for (Area c : cuts)
			a.subtract(c);
		return a;
	}

	static Area plus(Area a, Area... adds) {
		for (Area c : adds)
			a.add(c);
		return a;
	}

	/** A sword pointing up, centred on x 12: blade, crossguard, grip, pommel. */
	static Area sword() {
		Area s = poly(12, 1.5, 13.6, 4, 13.6, 15, 10.4, 15, 10.4, 4);
		s.add(rr(6.5, 14.6, 11, 2.4, 1.4));
		s.add(rect(11, 17, 2, 4));
		s.add(circle(12, 21.8, 1.6));
		s.subtract(rect(11.7, 4.5, 0.6, 9.5)); // fuller
		return s;
	}

	public static Shape shape(String name) {
		switch (name) {
		case "grid": {
			Area s = rr(3, 3, 8, 8, 2.5);
			s.add(rr(13, 3, 8, 8, 2.5));
			s.add(rr(3, 13, 8, 8, 2.5));
			s.add(rr(13, 13, 8, 8, 2.5));
			return s;
		}
		case "check": {
			Area s = circle(12, 12, 10);
			return minus(s, stroke(path(6.8, 12.4, 10.4, 16, 17.4, 8.4), 2.8));
		}
		case "lock": {
			Area s = rr(4.5, 10, 15, 12, 3);
			s.add(minus(rr(7, 2.5, 10, 14, 10), rr(9.6, 5.1, 4.8, 11, 4.8)));
			s.subtract(circle(12, 15, 2));
			s.subtract(rect(11.2, 15, 1.6, 4));
			return s;
		}
		case "scroll": {
			Area s = rr(6, 3, 13, 18, 2);
			s.add(circle(6, 5.5, 2.8));
			s.add(circle(18, 18.5, 2.8));
			s.add(rr(3.2, 3, 3, 5, 1));
			s.subtract(circle(6, 5.5, 1.1));
			s.subtract(circle(18, 18.5, 1.1));
			s.subtract(rect(9, 8, 7, 1.4));
			s.subtract(rect(9, 11.3, 7, 1.4));
			s.subtract(rect(9, 14.6, 5, 1.4));
			return s;
		}
		case "crown": {
			Area s = poly(3, 8, 7.5, 12.5, 12, 4.5, 16.5, 12.5, 21, 8, 19.5, 18, 4.5, 18);
			s.add(rr(4.5, 18.6, 15, 2.8, 1));
			s.add(circle(3, 7.4, 1.7));
			s.add(circle(12, 3.6, 1.8));
			s.add(circle(21, 7.4, 1.7));
			s.subtract(circle(12, 14.2, 1.6));
			return s;
		}
		case "skull": {
			Area s = circle(12, 10, 8.5);
			s.add(rr(7.5, 13, 9, 8.5, 3));
			s.subtract(circle(8.8, 10.6, 2.4));
			s.subtract(circle(15.2, 10.6, 2.4));
			s.subtract(poly(12, 13.3, 13.3, 16, 10.7, 16));
			s.subtract(rect(9.8, 18.5, 1.1, 3));
			s.subtract(rect(13.1, 18.5, 1.1, 3));
			return s;
		}
		case "swords": {
			Area a = rot(sword(), 40, 12, 12);
			Area b = rot(sword(), -40, 12, 12);
			Area gap = rot(rr(5.5, 13.4, 13, 4.6, 2), -40, 12, 12); // a sliver where b crosses over a
			a.subtract(stroke(b, 1.6));
			a.add(b);
			return a;
		}
		case "pickaxe": {
			Area head = new Area(new Arc2D.Double(2, 3, 20, 14, 20, 140, Arc2D.OPEN));
			head = stroke(new Arc2D.Double(3, 4, 18, 13, 25, 130, Arc2D.OPEN), 3.2);
			head.add(poly(2.4, 9.6, 5.2, 7.2, 3.6, 12.6));
			head.add(poly(21.6, 9.6, 18.8, 7.2, 20.4, 12.6));
			Area handle = line(12, 5.4, 12, 21.5, 2.6);
			Area s = rot(plus(head, handle), 35, 12, 12);
			return s;
		}
		case "gem": {
			Area s = poly(6, 4, 18, 4, 22, 9.5, 12, 21, 2, 9.5);
			s.subtract(stroke(path(2.6, 9.5, 21.4, 9.5), 1.1));
			s.subtract(stroke(path(9, 4.4, 12, 9.5, 15, 4.4), 1.1));
			s.subtract(stroke(path(7.4, 9.5, 12, 20, 16.6, 9.5), 1.1));
			return s;
		}
		case "star": {
			Path2D p = new Path2D.Double();
			for (int i = 0; i < 10; i++) {
				double r = i % 2 == 0 ? 10.5 : 4.4, t = Math.toRadians(-90 + i * 36);
				double x = 12 + r * Math.cos(t), y = 12.6 + r * Math.sin(t);
				if (i == 0) p.moveTo(x, y);
				else p.lineTo(x, y);
			}
			p.closePath();
			return a(p);
		}
		case "flame": {
			Path2D p = new Path2D.Double();
			p.moveTo(12, 1.5);
			p.curveTo(13, 6, 19.5, 8.5, 19.5, 14.5);
			p.curveTo(19.5, 19, 16, 22.5, 12, 22.5);
			p.curveTo(8, 22.5, 4.5, 19, 4.5, 14.5);
			p.curveTo(4.5, 11, 7, 9.5, 8, 7);
			p.curveTo(8.6, 9, 9.5, 10, 10.6, 10.4);
			p.curveTo(10.4, 7, 11, 4, 12, 1.5);
			p.closePath();
			Area s = a(p);
			Path2D inner = new Path2D.Double();
			inner.moveTo(12, 11);
			inner.curveTo(12.5, 14, 15.6, 15, 15.6, 18);
			inner.curveTo(15.6, 20.2, 14, 21.2, 12, 21.2);
			inner.curveTo(10, 21.2, 8.4, 20.2, 8.4, 18);
			inner.curveTo(8.4, 15.5, 11.5, 14.5, 12, 11);
			inner.closePath();
			s.subtract(a(inner));
			return s;
		}
		case "calendar": {
			Area s = rr(3, 5, 18, 16.5, 3);
			s.subtract(rect(3, 9, 18, 1.4));
			for (int r = 0; r < 2; r++)
				for (int c = 0; c < 3; c++)
					s.subtract(rr(6 + c * 4.5, 12 + r * 4.2, 3, 2.6, 0.8));
			s.add(rr(6.5, 2.5, 2.4, 5, 1.2));
			s.add(rr(15.1, 2.5, 2.4, 5, 1.2));
			return s;
		}
		case "ballot": {
			Area s = rr(3, 11, 18, 10.5, 2);
			s.subtract(rect(7, 13.5, 10, 1.6));
			Area paper = rot(rr(7.5, 2.5, 9, 11, 1.2), 12, 12, 8);
			Area tick = rot(stroke(path(9.6, 7.6, 11.4, 9.6, 14.8, 5.6), 1.6), 12, 12, 8);
			paper.subtract(tick);
			paper.subtract(rect(3, 11, 18, 3.5));
			s.add(paper);
			return s;
		}
		case "gift": {
			Area s = rr(4, 11, 16, 10.5, 1.5);
			s.add(rr(3, 7.4, 18, 4.4, 1.2));
			s.subtract(rect(11, 7.4, 2, 14));
			Area bow = circle(9, 5.2, 2.6);
			bow.add(circle(15, 5.2, 2.6));
			bow.subtract(circle(9, 5.2, 1.1));
			bow.subtract(circle(15, 5.2, 1.1));
			s.add(bow);
			return s;
		}
		case "coin": {
			Area s = circle(12, 12, 10);
			s.subtract(stroke(new Ellipse2D.Double(5, 5, 14, 14), 1.2));
			s.subtract(stroke(path(14.6, 8.6, 13.4, 7.8, 10.8, 7.8, 9.4, 9.2, 10.2, 11.2, 13.8, 12.6, 14.6, 14.6, 13.2, 16.2, 10.6, 16.2, 9.2, 15.4), 1.6));
			s.subtract(rect(11.3, 6, 1.4, 2));
			s.subtract(rect(11.3, 16, 1.4, 2));
			return s;
		}
		case "anvil": {
			Area s = poly(2, 6.5, 19, 6.5, 21.5, 8.5, 17, 11.5, 15.5, 11.5, 15, 15, 17.5, 17.5, 6.5, 17.5, 9, 15, 8.5, 11.5, 6, 11.5, 2.5, 9);
			s.add(rr(5, 18, 14, 3.5, 1));
			return s;
		}
		case "hammer": {
			Area head = rr(4, 3, 14, 6.6, 1.6);
			head.add(poly(18, 3.6, 21.5, 2.4, 21.5, 10.2, 18, 9));
			Area handle = rr(9.4, 9, 3.2, 13, 1.6);
			return rot(plus(head, handle), 35, 12, 12);
		}
		case "sparkle": {
			Area s = poly(12, 1.5, 14, 10, 22.5, 12, 14, 14, 12, 22.5, 10, 14, 1.5, 12, 10, 10);
			s.add(poly(19, 2.5, 19.8, 5.2, 22.5, 6, 19.8, 6.8, 19, 9.5, 18.2, 6.8, 15.5, 6, 18.2, 5.2));
			return s;
		}
		case "rune": {
			Area s = rr(3.5, 2, 17, 20, 7);
			s.subtract(stroke(path(12, 5.5, 12, 18.5), 1.8));
			s.subtract(stroke(path(8, 8.5, 12, 12, 16, 8.5), 1.8));
			s.subtract(stroke(path(8.5, 16.5, 12, 13.5, 15.5, 16.5), 1.8));
			return s;
		}
		case "shield": {
			Path2D p = new Path2D.Double();
			p.moveTo(12, 2);
			p.lineTo(20.5, 5);
			p.curveTo(20.5, 13, 17.5, 18.6, 12, 22);
			p.curveTo(6.5, 18.6, 3.5, 13, 3.5, 5);
			p.closePath();
			Area s = a(p);
			Path2D q = new Path2D.Double();
			q.moveTo(12, 5.2);
			q.lineTo(17.6, 7.2);
			q.curveTo(17.4, 12.6, 15.4, 16.6, 12, 18.8);
			q.closePath();
			s.subtract(a(q));
			return s;
		}
		case "bow": {
			Area s = stroke(new Arc2D.Double(-1, 2, 18, 20, -62, 124, Arc2D.OPEN), 2.6);
			s.add(line(12.2, 3.8, 12.2, 20.2, 0.9));
			s.add(line(4, 12, 21.5, 12, 1.5));
			s.add(poly(22.5, 12, 18.5, 9.4, 18.5, 14.6));
			s.add(poly(4, 12, 2, 9.8, 6.2, 12, 2, 14.2));
			return s;
		}
		case "staff": {
			Area s = line(6, 21, 15.2, 8.6, 2.4);
			s.add(circle(17, 6.4, 4.4));
			s.subtract(circle(17, 6.4, 2.2));
			s.add(circle(17, 6.4, 1.2));
			return s;
		}
		case "hourglass": {
			Area s = rr(5, 2, 14, 2.6, 1);
			s.add(rr(5, 19.4, 14, 2.6, 1));
			s.add(poly(6.5, 4.5, 17.5, 4.5, 17.5, 6.5, 13.4, 12, 17.5, 17.5, 17.5, 19.5, 6.5, 19.5, 6.5, 17.5, 10.6, 12, 6.5, 6.5));
			s.subtract(poly(8.6, 6.2, 15.4, 6.2, 12, 10.6));
			s.subtract(poly(12, 15, 15.4, 18.2, 8.6, 18.2));
			s.add(poly(9.6, 18.2, 14.4, 18.2, 12, 15.8));
			return s;
		}
		case "arrow_left":
			return poly(15.5, 4, 15.5, 20, 6.5, 12);
		case "arrow_right":
			return poly(8.5, 4, 8.5, 20, 17.5, 12);
		case "search": {
			Area s = circle(10, 10, 7.5);
			s.subtract(circle(10, 10, 5));
			s.add(line(15.4, 15.4, 21, 21, 3.2));
			return s;
		}
		case "trophy": {
			Area s = poly(6, 3, 18, 3, 17.4, 9.5, 15, 13.5, 13, 14.5, 13, 17.5, 16, 19, 16, 21, 8, 21, 8, 19, 11, 17.5, 11, 14.5, 9, 13.5, 6.6, 9.5);
			s.add(stroke(new Arc2D.Double(2.5, 4, 6, 6.5, 90, 180, Arc2D.OPEN), 1.6));
			s.add(stroke(new Arc2D.Double(15.5, 4, 6, 6.5, -90, 180, Arc2D.OPEN), 1.6));
			s.subtract(rect(8.6, 5.4, 1.2, 4));
			return s;
		}
		case "portal": {
			Area s = a(new Ellipse2D.Double(4, 2, 16, 20));
			s.subtract(a(new Ellipse2D.Double(6.5, 4.5, 11, 15)));
			Area swirl = stroke(new Arc2D.Double(8.5, 7, 7, 10, 60, 250, Arc2D.OPEN), 1.6);
			swirl.add(circle(12, 12, 1.4));
			s.add(swirl);
			return s;
		}
		case "pin": {
			Path2D p = new Path2D.Double();
			p.moveTo(12, 22.5);
			p.curveTo(7, 16, 4.5, 12.6, 4.5, 9);
			p.curveTo(4.5, 4.8, 7.8, 1.5, 12, 1.5);
			p.curveTo(16.2, 1.5, 19.5, 4.8, 19.5, 9);
			p.curveTo(19.5, 12.6, 17, 16, 12, 22.5);
			p.closePath();
			return minus(a(p), circle(12, 9, 3));
		}
		case "castle": {
			Area s = rect(4, 9, 16, 13);
			for (int i = 0; i < 4; i++)
				s.add(rect(4 + i * 4.5, 6, 2.6, 3.4));
			s.add(rect(9.5, 2.5, 5, 7));
			s.add(rect(9.5, 1, 1.4, 2));
			s.add(rect(13.1, 1, 1.4, 2));
			s.subtract(rr(10, 15, 4, 7, 4));
			s.subtract(rect(6.5, 12, 1.8, 2.6));
			s.subtract(rect(15.7, 12, 1.8, 2.6));
			return s;
		}
		case "dragon": {
			Path2D p = new Path2D.Double(); // a dragon head in profile, facing right
			p.moveTo(3, 15);
			p.curveTo(3, 9, 7, 5.5, 11.5, 5);
			p.lineTo(9.5, 1.5);
			p.lineTo(14.5, 4.6);
			p.curveTo(17, 5, 19, 6.5, 21.5, 9);
			p.lineTo(17.6, 10.4);
			p.lineTo(21.5, 13.2);
			p.curveTo(17, 15.5, 13, 15.5, 11, 14.5);
			p.curveTo(10.2, 18, 8.2, 21, 5, 22.5);
			p.curveTo(5.6, 19.5, 4, 17, 3, 15);
			p.closePath();
			Area s = a(p);
			s.subtract(poly(13.2, 7.2, 16, 7.6, 13.6, 8.8));
			return s;
		}
		case "leaf": {
			Path2D p = new Path2D.Double();
			p.moveTo(4, 20);
			p.curveTo(3, 10, 9, 3.5, 20.5, 3);
			p.curveTo(21, 14, 14, 20.5, 4, 20);
			p.closePath();
			Area s = a(p);
			s.subtract(stroke(path(5, 19, 16.5, 7.5), 1.2));
			return s;
		}
		case "fish": {
			Area s = a(new Ellipse2D.Double(2.5, 7, 15, 10));
			s.add(poly(15.5, 12, 22, 6.5, 21, 12, 22, 17.5));
			s.subtract(circle(7, 10.8, 1.3));
			s.subtract(stroke(new Arc2D.Double(8.5, 8.5, 6, 7, -70, 140, Arc2D.OPEN), 1));
			return s;
		}
		case "house": {
			Area s = poly(12, 2.5, 22, 11, 19.5, 11, 19.5, 21, 4.5, 21, 4.5, 11, 2, 11);
			s.subtract(rr(10, 14, 4, 7, 1));
			return s;
		}
		case "dice": {
			Area s = rr(3, 3, 18, 18, 4.5);
			for (double[] d : new double[][] { { 8, 8 }, { 16, 8 }, { 12, 12 }, { 8, 16 }, { 16, 16 } })
				s.subtract(circle(d[0], d[1], 1.7));
			return s;
		}
		case "book": {
			Area s = poly(2, 5, 11, 6.5, 11, 21, 2, 19.5);
			s.add(poly(13, 6.5, 22, 5, 22, 19.5, 13, 21));
			s.subtract(rect(4, 9, 5, 1.2));
			s.subtract(rect(4, 12, 5, 1.2));
			s.subtract(rect(15, 9, 5, 1.2));
			s.subtract(rect(15, 12, 5, 1.2));
			return s;
		}
		case "bolt":
			return poly(13.5, 1.5, 4.5, 13.5, 11, 13.5, 9.5, 22.5, 19.5, 9.5, 12.8, 9.5);
		case "eye": {
			Path2D p = new Path2D.Double();
			p.moveTo(1.5, 12);
			p.curveTo(5.5, 5.5, 18.5, 5.5, 22.5, 12);
			p.curveTo(18.5, 18.5, 5.5, 18.5, 1.5, 12);
			p.closePath();
			Area s = a(p);
			s.subtract(circle(12, 12, 4.2));
			s.add(circle(12, 12, 2.2));
			return s;
		}
		case "heart": {
			Path2D p = new Path2D.Double();
			p.moveTo(12, 21);
			p.curveTo(4, 15, 2, 11, 2, 8);
			p.curveTo(2, 4.5, 4.8, 2.5, 7.4, 2.5);
			p.curveTo(9.4, 2.5, 11, 3.6, 12, 5.4);
			p.curveTo(13, 3.6, 14.6, 2.5, 16.6, 2.5);
			p.curveTo(19.2, 2.5, 22, 4.5, 22, 8);
			p.curveTo(22, 11, 20, 15, 12, 21);
			p.closePath();
			return a(p);
		}
		case "chest": {
			Area s = rr(3, 9, 18, 12, 1.5);
			s.add(a(new Arc2D.Double(3, 3, 18, 12, 0, 180, Arc2D.CHORD)));
			s.subtract(rect(3, 9.5, 18, 1.3));
			s.subtract(rr(10, 10.6, 4, 4.6, 1));
			return s;
		}
		case "paw": {
			Area s = a(new Ellipse2D.Double(7, 11.5, 10, 9));
			s.add(a(new Ellipse2D.Double(3, 7.5, 4.4, 5.6)));
			s.add(a(new Ellipse2D.Double(16.6, 7.5, 4.4, 5.6)));
			s.add(a(new Ellipse2D.Double(7.2, 3, 4.2, 5.6)));
			s.add(a(new Ellipse2D.Double(12.6, 3, 4.2, 5.6)));
			return s;
		}
		case "globe": {
			Area s = circle(12, 12, 10);
			s.subtract(stroke(new Ellipse2D.Double(7.5, 2, 9, 20), 1.2));
			s.subtract(rect(2, 11.4, 20, 1.2));
			s.subtract(stroke(new Arc2D.Double(3, 4, 18, 6, 180, 180, Arc2D.OPEN), 1.1));
			s.subtract(stroke(new Arc2D.Double(3, 14, 18, 6, 0, 180, Arc2D.OPEN), 1.1));
			return s;
		}
		case "mask": { // minigames: a theatre mask
			Path2D p = new Path2D.Double();
			p.moveTo(3, 4);
			p.curveTo(9, 6, 15, 6, 21, 4);
			p.curveTo(21.5, 14, 17.5, 21, 12, 21);
			p.curveTo(6.5, 21, 2.5, 14, 3, 4);
			p.closePath();
			Area s = a(p);
			s.subtract(a(new Ellipse2D.Double(6, 9, 4.6, 3)));
			s.subtract(a(new Ellipse2D.Double(13.4, 9, 4.6, 3)));
			s.subtract(stroke(new Arc2D.Double(8, 11.5, 8, 6, 200, 140, Arc2D.OPEN), 1.6));
			return s;
		}
		case "plus": {
			Area s = rr(10, 3, 4, 18, 1.5);
			s.add(rr(3, 10, 18, 4, 1.5));
			return s;
		}
		case "minus":
			return rr(3, 10, 18, 4, 1.5);
		case "x":
			return plus(line(5, 5, 19, 19, 3.2), line(19, 5, 5, 19, 3.2));
		case "diamond":
			return poly(12, 2, 22, 12, 12, 22, 2, 12);
		default:
			throw new IllegalArgumentException("no icon " + name);
		}
	}

	static Path2D path(double... xy) {
		Path2D p = new Path2D.Double();
		p.moveTo(xy[0], xy[1]);
		for (int i = 2; i < xy.length; i += 2)
			p.lineTo(xy[i], xy[i + 1]);
		return p;
	}
}
