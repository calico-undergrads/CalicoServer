package calico.plugins.iip.graph.layout;

import java.awt.Point;
import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.List;

import calico.networking.netstuff.CalicoPacket;
import calico.plugins.iip.IntentionalInterfacesNetworkCommands;

/**
 * The cluster layout is visually organized according to simple geometric shapes like concentric circles. This topology
 * describes those visual geometry elements, such as the radii of the concentric circles for each cluster.
 * 
 * @author Byron Hawkins
 */
public class CIntentionTopology
{
	/**
	 * Describes the topology for one cluster.
	 * 
	 * @author Byron Hawkins
	 */
	public class Cluster
	{
		private final long rootCanvasId;
		/**
		 * Center of this cluster in the IntentionView.
		 */
		private final Point center = new Point();
		/**
		 * Radii of the concentric circles for this cluster.
		 */
		private final List<Integer> radii = new ArrayList<Integer>();
		/**
		 * A box that tightly contains all canvases in the cluster.
		 */
		private final Rectangle boundingBox = new Rectangle();

		/**
		 * Construct a new cluster topology by extracting information from the cluster's current layout.
		 */
		Cluster(CIntentionClusterLayout clusterLayout)
		{
			rootCanvasId = clusterLayout.getCluster().getRootCanvasId();
			center.setLocation(clusterLayout.getCluster().getLocation());

			for (Double radius : clusterLayout.getCluster().getRingRadii())
			{
				radii.add(radius.intValue());
			}

			boundingBox.setSize(clusterLayout.getBoundingBox());
			Point layoutCenter = clusterLayout.getLayoutCenterWithinBounds(boundingBox.getSize());
			boundingBox.setLocation(center.x - layoutCenter.x, center.y - layoutCenter.y);
		}

		/**
		 * Write the topology for this cluster to <code>buffer</code> for network transport or persistence.
		 */
		void serialize(StringBuilder buffer)
		{
			buffer.append(rootCanvasId);
			buffer.append("[");
			buffer.append(center.x);
			buffer.append(",");
			buffer.append(center.y);
			buffer.append(",");
			buffer.append(boundingBox.x);
			buffer.append(",");
			buffer.append(boundingBox.y);
			buffer.append(",");
			buffer.append(boundingBox.width);
			buffer.append(",");
			buffer.append(boundingBox.height);
			buffer.append(":");

			for (Integer radius : radii)
			{
				buffer.append(radius);
				buffer.append(",");
			}
			buffer.setLength(buffer.length() - 1);
			buffer.append("]");
		}

		public Point getCenter()
		{
			return center;
		}

		public List<Integer> getRadii()
		{
			return radii;
		}
	}

	private final List<Cluster> clusters = new ArrayList<Cluster>();

	public CIntentionTopology()
	{
	}

	/**
	 * Create a <code>CalicoPacket</code> containing all topology information for all clusters.
	 */
	public CalicoPacket createPacket()
	{
		CalicoPacket p = new CalicoPacket();
		p.putInt(IntentionalInterfacesNetworkCommands.CIC_TOPOLOGY);
		p.putString(serialize());
		return p;
	}

	public void clear()
	{
		clusters.clear();
	}

	public List<Cluster> getClusters()
	{
		return clusters;
	}

	public void addCluster(CIntentionClusterLayout clusterLayout)
	{
		clusters.add(new Cluster(clusterLayout));
	}

	private String serialize()
	{
		StringBuilder buffer = new StringBuilder();
		for (Cluster cluster : clusters)
		{
			buffer.append("C");
			cluster.serialize(buffer);
		}
		return buffer.toString();
	}
}
