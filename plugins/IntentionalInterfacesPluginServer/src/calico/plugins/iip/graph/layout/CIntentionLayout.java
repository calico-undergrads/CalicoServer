package calico.plugins.iip.graph.layout;

import java.awt.Dimension;
import java.awt.Point;
import java.util.List;

import calico.controllers.CCanvasController;
import calico.plugins.iip.CCanvasLink;
import calico.plugins.iip.IntentionalInterfaceState;
import calico.plugins.iip.controllers.CCanvasLinkController;

/**
 * Entry point for invocation of layout operations. Nothing is calculated here, it only serves to coordinate requests
 * (coming in from outside the layout) with the layout's internal data structures.
 * 
 * @author Byron Hawkins
 */
public class CIntentionLayout
{
	private static final CIntentionLayout INSTANCE = new CIntentionLayout();

	public static CIntentionLayout getInstance()
	{
		return INSTANCE;
	}

	private static int calculateCellDiameter(Dimension cellSize)
	{
		return ((int) Math.sqrt((cellSize.height * cellSize.height) + (double) (cellSize.width * cellSize.width)));
	}

	public static Point centerCanvasAt(int x, int y)
	{
		return new Point(x - (CIntentionLayout.INTENTION_CELL_SIZE.width / 2), y - (CIntentionLayout.INTENTION_CELL_SIZE.height / 2));
	}

	/**
	 * The pixel dimensions of a canvas thumbnail, as rendered in the IntentionView. The term "cell" is an abbreviation
	 * of "canvas thumbnail rectangle".
	 */
	public static final Dimension INTENTION_CELL_SIZE = new Dimension(200, 130);
	/**
	 * Although canvas thumbnails are rectangular, they are regarded as circular by the layout. This constant maintains
	 * the diameter of the bounding circle of a canvas.
	 */
	static final int INTENTION_CELL_DIAMETER = calculateCellDiameter(INTENTION_CELL_SIZE);

	/**
	 * Singleton instance of the cluster graph.
	 */
	private final CIntentionClusterGraph graph = new CIntentionClusterGraph();
	/**
	 * Singleton instance of the cluster topology.
	 */
	private final CIntentionTopology topology = new CIntentionTopology();

	public CIntentionTopology getTopology()
	{
		return topology;
	}

	/**
	 * Serlialize current layout data for persistence.
	 */
	public void populateState(IntentionalInterfaceState state)
	{
		state.setTopologyPacket(topology.createPacket());
		state.setClusterGraphPacket(graph.createPacket());
	}

	/**
	 * Inflate the cluster graph from a persisted copy.
	 */
	public void inflateStoredClusterGraph(String graphData)
	{
		graph.inflateStoredData(graphData);
	}

	/**
	 * Get the id of the canvas at the center of the cluster containing <code>canvasId</code>. A cluster is most often
	 * referred to by the id of its root (central) canvas.
	 */
	public long getRootCanvasId(long canvasId)
	{
		while (true)
		{ // walk to the root of the cluster
			Long linkId = CCanvasLinkController.getInstance().getIncomingLink(canvasId);
			if (linkId == null)
			{
				break;
			}
			else
			{
				CCanvasLink link = CCanvasLinkController.getInstance().getLink(linkId);
				canvasId = link.getAnchorA().getCanvasId();
			}
		}
		return canvasId;
	}

	/**
	 * When the root canvas of a cluster is deleted, the cluster loses its identity, because it was identifying itself
	 * using that canvas's id. This method allows a new (existing) canvas to become the root canvas in place of the
	 * original (now deleted) canvas.
	 */
	public void replaceCluster(long originalRootCanvasId, long newRootCanvasId)
	{
		CIntentionCluster cluster = new CIntentionCluster(newRootCanvasId);
		graph.replaceCluster(originalRootCanvasId, cluster);
	}

	public void insertCluster(long rootCanvasId)
	{
		CIntentionCluster cluster = new CIntentionCluster(rootCanvasId);
		graph.insertCluster(cluster);
	}

	public void insertCluster(long contextCanvasId, long rootCanvasId)
	{
		CIntentionCluster cluster = new CIntentionCluster(rootCanvasId);
		graph.insertCluster(getRootCanvasId(contextCanvasId), cluster);
	}

	public void removeClusterIfAny(long rootCanvasId)
	{
		graph.removeClusterIfAny(rootCanvasId);
	}

	/**
	 * Apply all layout operations according to the current structure of the canvas links.
	 */
	public List<CIntentionClusterLayout> layoutGraph()
	{
		topology.clear();

		List<CIntentionClusterLayout> clusterLayouts = graph.layoutClusters();
		for (CIntentionClusterLayout clusterLayout : clusterLayouts)
		{
			topology.addCluster(clusterLayout);
		}

		graph.reset();

		return clusterLayouts;
	}

	/**
	 * The canvas ids are sparsely distributed among all UUIDs in the Calico state. To make canvases numbering
	 * recognizable to the user, an index is assigned to each canvas, such that canvas indexes are sequential from 1.
	 * This method gets the index of a canvas from its id.
	 */
	static int getCanvasIndex(long canvasId)
	{
		if (canvasId < 0L)
		{
			return -1;
		}
		return CCanvasController.canvases.get(canvasId).getIndex();
	}
}
