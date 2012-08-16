package calico.plugins.iip.graph.layout;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

/**
 * Container for the layout of one cluster. The layout of canvases within the cluster is not handled here, that work is
 * done instead in CIntentionCluster. Simple adjustments are made here, such as centering the layout within a bounding
 * box, or moving the entire cluster according to instruction received from the CIntentionClusterGraph.
 * 
 * @author Byron Hawkins
 */
public class CIntentionClusterLayout
{
	/**
	 * Public container for the position of each canvas in this cluster. Positions are global to the IntentionView, not
	 * relative to the cluster.
	 * 
	 * @author Byron Hawkins
	 */
	public class CanvasPosition
	{
		public final long canvasId;
		public final Point location;

		CanvasPosition(long canvasId, Point location)
		{
			this.canvasId = canvasId;
			this.location = location;
		}

		void translateBy(int x, int y)
		{
			location.x += x;
			location.y += y;
		}
	}

	/**
	 * The cluster for which layout is being organized.
	 */
	private final CIntentionCluster cluster;
	/**
	 * Set of canvas positions.
	 */
	private final List<CanvasPosition> canvasPositions = new ArrayList<CanvasPosition>();

	// transitory values, which are refreshed during each call to <code>getBoundingBox()</code>, and cleared on
	// <code>reset()</code>
	private boolean isCalculated = false;
	private final Point rootCanvasPosition = new Point();
	private final Dimension boundingBox = new Dimension();

	CIntentionClusterLayout(CIntentionCluster cluster)
	{
		this.cluster = cluster;
	}

	/**
	 * Reset transitory data related to the position of the whole cluster.
	 */
	void reset()
	{
		isCalculated = false;
		rootCanvasPosition.setLocation(0, 0);
		boundingBox.setSize(0, 0);
	}

	public CIntentionCluster getCluster()
	{
		return cluster;
	}

	public List<CanvasPosition> getCanvasPositions()
	{
		return canvasPositions;
	}

	void addCanvas(long canvasId, Point location)
	{
		canvasPositions.add(new CanvasPosition(canvasId, location));
	}

	/**
	 * Calculate the center of this cluster such that the cluster appears exactly in the center of <code>bounds</code>
	 */
	Point getLayoutCenterWithinBounds(Dimension bounds)
	{
		if (!isCalculated)
			calculate();

		int xInset = (bounds.width - boundingBox.width) / 2;
		int yInset = (bounds.height - boundingBox.height) / 2;
		return new Point(rootCanvasPosition.x + xInset + (CIntentionLayout.INTENTION_CELL_SIZE.width / 2), rootCanvasPosition.y + yInset
				+ (CIntentionLayout.INTENTION_CELL_SIZE.height / 2));
	}

	/**
	 * Get the size of the box that tightly fits all canvases in this cluster.
	 */
	public Dimension getBoundingBox()
	{
		if (!isCalculated)
			calculate();

		return boundingBox;
	}

	private void calculate()
	{
		int xMin = Integer.MAX_VALUE;
		int xMax = -Integer.MAX_VALUE;
		int yMin = Integer.MAX_VALUE;
		int yMax = -Integer.MAX_VALUE;

		CanvasPosition rootCanvas = null;

		for (CanvasPosition position : canvasPositions)
		{
			xMin = Math.min(position.location.x, xMin);
			yMin = Math.min(position.location.y, yMin);
			xMax = Math.max(position.location.x + CIntentionLayout.INTENTION_CELL_SIZE.width, xMax);
			yMax = Math.max(position.location.y + CIntentionLayout.INTENTION_CELL_SIZE.height, yMax);

			if (position.canvasId == cluster.getRootCanvasId())
			{
				rootCanvas = position;
			}
		}

		rootCanvasPosition.x = (rootCanvas.location.x - (xMin - 10)); // clumsy handling of the buffer spacing
		rootCanvasPosition.y = (rootCanvas.location.y - (yMin - 10));

		boundingBox.width = (xMax - xMin) + 20;
		boundingBox.height = (yMax - yMin) + 20;

		isCalculated = true;
	}
}
