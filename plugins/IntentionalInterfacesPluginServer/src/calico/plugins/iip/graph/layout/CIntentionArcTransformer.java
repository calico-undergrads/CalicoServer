package calico.plugins.iip.graph.layout;

import java.awt.Point;

/**
 * Transforms a 1-dimensional representation of a CIntentionRing into the 2-dimensional ring, as it appears in the
 * layout. The CIntentionRing and its slices operate in only 1 dimension (a plain straight line) because it's easier
 * that way.
 * 
 * A transformer is stateful, as if each instance is a "transforming session". One transformer instance may only be used
 * to transform positions for one CIntentionRing, and it executes all operations as if "focused" on that ring.
 * 
 * @author Byron Hawkins
 */
class CIntentionArcTransformer
{
	/**
	 * the center of the CIntentionCluster containing the focused ring.
	 */
	private final Point center;
	/**
	 * the radius of the focused ring
	 */
	private final double radius;
	/**
	 * the circumference of the focused ring
	 */
	private final int ringSpan;
	/**
	 * the offset of the arc position referred to by (xArc=0). By default, the algorithm will map (xArc=0) to 3 o'clock
	 * on the ring. Given the configuration slices, a CIntentionRing will usually want (xArc=0) to map somewhere else on
	 * the visual ring.
	 * 
	 * This is important for simplicity, because the CIntentionRing is internally represented as a straight line, and
	 * the "arcing" process bends the line around so that the two endpoints meet at a single point. When the
	 * CIntentionSlice sends (xArc=0), it is referring to that join point. So the join point must be rotated to the
	 * point where the CIntentionRing has decided to start its first arc.
	 */
	private final double offset;

	CIntentionArcTransformer(Point center, double radius, int ringSpan, int firstArcSpan)
	{
		this.center = center;
		this.radius = radius;
		this.ringSpan = ringSpan;

		offset = ((7 * ringSpan) / 8.0) - (firstArcSpan / 2.0);

		// System.out.println("Offset " + offset + " for radius " + radius + " and ring span " + ringSpan +
		// " and first arc " + firstArcSpan);
	}

	/**
	 * Calculate the position of the upper left corner of a canvas such that its center lies <code>xArc</code> pixels
	 * along the arc (starting from 3 o'clock and adjusted by <code>this.offset</code>).
	 */
	Point centerCanvasAt(int xArc)
	{
		int xShiftedArc = (int) ((xArc + offset) % ringSpan);
		return centerCanvasAt(xShiftedArc, center, radius);
	}

	/**
	 * Calculate the ideal center position for a group of canvases, given the xArc position of the group's parent, and
	 * the radius of the parent ring. A "group of canvases" is a set of siblings in the intention graph, and the parent
	 * is of course their common parent, which is in the next smallest ring. The result of this function is the position
	 * which would ideally be the center of the group; if the arc is crowded, the group may be forced off to one side.
	 * 
	 * @param parentArcPosition
	 *            position of the parent canvas on its arc (not the position on the ring, specifically on the arc!)
	 * @param parentRingRadius
	 *            radius of the parent ring, required to adjust the parent's position proportionally to
	 *            <code>this.radius</code>
	 */
	double calculateIdealPosition(int parentArcPosition, double parentRingRadius)
	{
		return (radius / parentRingRadius) * parentArcPosition;
	}

	private Point centerCanvasAt(int xArc, Point center, double radius)
	{
		double theta = xArc / radius;
		int x = center.x + (int) (radius * Math.cos(theta));
		int y = center.y + (int) (radius * Math.sin(theta));

		// System.out.println(String.format("[%d] (%d, %d) for xArc %d and radius %f",
		// CIntentionLayout.getCanvasIndex(canvasId), x, y, xArc, radius));

		return CIntentionLayout.centerCanvasAt(x, y);
	}
}
