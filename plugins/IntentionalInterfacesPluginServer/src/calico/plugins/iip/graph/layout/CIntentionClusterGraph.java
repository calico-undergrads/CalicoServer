package calico.plugins.iip.graph.layout;

import it.unimi.dsi.fastutil.longs.Long2ObjectOpenHashMap;

import java.awt.Dimension;
import java.awt.Point;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import calico.networking.netstuff.CalicoPacket;
import calico.plugins.iip.IntentionalInterfacesNetworkCommands;

/**
 * Maintains the layout of the clusters on the IntentionView. Call <code>reset()</code> before each layout iteration.
 * Otherwise this class can be regarded as stateless.
 * 
 * @author Byron Hawkins
 */
public class CIntentionClusterGraph
{
	/**
	 * Maintains the position of one cluster.
	 * 
	 * Imagine that the IntentionView sits on "graph paper" having cells the size of
	 * CIntentionCluster.CLUSTER_UNIT_SIZE. Each instance of Position specifies (x,y) coordinates in the "graph paper"
	 * as <code>xUnit</code> and <code>yUnit</code>, and also specifies the number of graph paper cells occupied as
	 * <code>xUnitSapn</code> and <code>yUnitSpan</code>.
	 * 
	 * Imagine also that the IntentionView consists of an imaginary grid of clusters, each having no size. Each instance
	 * of Position specifies (x,y) coordinates on this grid as <code>rowIndex</code> and <code>columnIndex</code>.
	 * 
	 * @author Byron Hawkins
	 */
	private static class Position
	{
		/**
		 * x position of this cluster on the imaginary "graph paper" of unit clusters
		 */
		int xUnit;
		int yUnit;
		int yUnitSpan;
		int xUnitSpan;

		int rowIndex;
		int columnIndex;

		/**
		 * The cluster for which this <code>Position</code> maintains position settings.
		 */
		CIntentionCluster cluster;
		/**
		 * The instance of the layout for <code>this.cluster</code>.
		 */
		CIntentionClusterLayout clusterLayout;

		/**
		 * Create a new position for a cluster positioned at the specified grid coordinates.
		 */
		Position(int rowIndex, int columnIndex)
		{
			xUnit = yUnit = -1;
			xUnitSpan = yUnitSpan = -1;
			cluster = null;
			clusterLayout = null;

			this.rowIndex = rowIndex;
			this.columnIndex = columnIndex;
		}

		/**
		 * Inflate a position from persistent storage.
		 */
		Position(String data)
		{
			StringTokenizer tokens = new StringTokenizer(data, ",");
			xUnit = Integer.parseInt(tokens.nextToken());
			yUnit = Integer.parseInt(tokens.nextToken());
			xUnitSpan = Integer.parseInt(tokens.nextToken());
			yUnitSpan = Integer.parseInt(tokens.nextToken());
			rowIndex = Integer.parseInt(tokens.nextToken());
			columnIndex = Integer.parseInt(tokens.nextToken());

			long clusterRootCanvasId = Long.parseLong(tokens.nextToken());
			if (clusterRootCanvasId > 0L)
			{
				cluster = new CIntentionCluster(clusterRootCanvasId);
			}
			else
			{
				cluster = null;
			}
		}

		void reset()
		{
			cluster.reset();

			if (clusterLayout != null)
				clusterLayout.reset();
		}

		boolean isEmpty()
		{
			return (cluster == null);
		}

		/**
		 * Return the pixel position of the rightmost edge of the rightmost "graph paper" cell occupied by this
		 * <code>Position</code>'s cluster.
		 */
		int getRightExtent()
		{
			return xUnit + xUnitSpan;
		}

		/**
		 * Return the pixel position of the Lower edge of the lowest "graph paper" cell occupied by this
		 * <code>Position</code>'s cluster.
		 */
		int getDownExtent()
		{
			return yUnit + yUnitSpan;
		}

		/**
		 * Layout this <code>Position</code>'s cluster as if it were floating in empty space (and not in any cluster
		 * graph).
		 */
		void layoutInEmptySpace()
		{
			clusterLayout = cluster.layoutClusterAsCircles(new Point());
		}

		/**
		 * Having laid out the cluster, now center it within the region of "graph paper" allocated to this
		 * <code>Position</code>.
		 */
		void centerLayoutInUnitBounds()
		{
			Point rootPosition = clusterLayout.getLayoutCenterWithinBounds(new Dimension(xUnitSpan * CIntentionCluster.CLUSTER_UNIT_SIZE.width, yUnitSpan
					* CIntentionCluster.CLUSTER_UNIT_SIZE.height));
			Point center = new Point((int) ((xUnit * CIntentionCluster.CLUSTER_UNIT_SIZE.width) + rootPosition.x),
					(int) ((yUnit * CIntentionCluster.CLUSTER_UNIT_SIZE.height) + rootPosition.y));

			for (CIntentionClusterLayout.CanvasPosition layoutPosition : clusterLayout.getCanvasPositions())
			{
				layoutPosition.translateBy(center.x, center.y);
			}

			cluster.setLocation(center);
		}

		/**
		 * Serialize this <code>Position</code> for network transport and/or persistent storage.
		 */
		void serialize(StringBuilder buffer)
		{
			buffer.append("[");
			buffer.append(xUnit);
			buffer.append(",");
			buffer.append(yUnit);
			buffer.append(",");
			buffer.append(xUnitSpan);
			buffer.append(",");
			buffer.append(yUnitSpan);
			buffer.append(",");
			buffer.append(rowIndex);
			buffer.append(",");
			buffer.append(columnIndex);
			buffer.append(",");

			if (cluster == null)
			{
				buffer.append(0L);
			}
			else
			{
				buffer.append(cluster.getRootCanvasId());
			}

			buffer.append("]");
		}
	}

	/**
	 * The invisible "graph paper" underlying the IntentionView.
	 * 
	 * @author Byron Hawkins
	 */
	private static class UnitGraph
	{
		private final List<Integer> boundary = new ArrayList<Integer>();
		private int yUnit = 0;

		private int getBoundary(int yUnit)
		{
			for (int i = boundary.size(); i <= yUnit; i++)
			{
				boundary.add(0);
			}
			return boundary.get(yUnit);
		}

		private int getMaxBoundary(Position position)
		{
			int xMax = getBoundary(position.yUnit);
			for (int y = 1; y < position.yUnitSpan; y++)
			{
				xMax = Math.max(xMax, getBoundary(position.yUnit + y));
			}
			return xMax;
		}

		void clear()
		{
			boundary.clear();
			yUnit = 0;
		}

		void nextColumn()
		{
			yUnit = 0;
		}

		void layout(Position position)
		{
			if (position.isEmpty())
			{
				position.xUnitSpan = position.yUnitSpan = 1; // 0 to auto-collapse empty cells
			}
			else
			{
				position.layoutInEmptySpace();

				Dimension boundingBox = position.clusterLayout.getBoundingBox();
				position.xUnitSpan = Math.max(1, (int) Math.ceil(boundingBox.width / CIntentionCluster.CLUSTER_UNIT_SIZE.getWidth()));
				position.yUnitSpan = Math.max(1, (int) Math.ceil(boundingBox.height / CIntentionCluster.CLUSTER_UNIT_SIZE.getHeight()));
			}

			position.yUnit = yUnit;
			position.xUnit = getMaxBoundary(position);

			for (int i = position.yUnit; i < (position.yUnit + position.yUnitSpan); i++)
			{
				boundary.set(i, position.xUnit + position.xUnitSpan);
			}

			yUnit += position.yUnitSpan;
		}
	}

	private enum Direction
	{
		DOWN,
		RIGHT;
	}

	/**
	 * The graph of "sizeless" cluster positions.
	 */
	private final List<List<Position>> graph = new ArrayList<List<Position>>();
	private int columnCount = 0;
	/**
	 * <code>Position</code>s indexed by the root canvas id of the corresponding cluster.
	 */
	private final Long2ObjectOpenHashMap<Position> positionsByRootCanvasId = new Long2ObjectOpenHashMap<Position>();

	/**
	 * Keep track of state which is lazily refreshed once per layout iteration.
	 */
	private boolean isCalculated = false;
	private final UnitGraph unitGraph = new UnitGraph();

	public CIntentionClusterGraph()
	{
		graph.add(new ArrayList<Position>()); // install one row
		addColumn(); // install one column
	}

	void reset()
	{
		unitGraph.clear();
		isCalculated = false;
		resetClusters();
	}

	private void resetClusters()
	{
		for (List<Position> row : graph)
		{
			for (Position position : row)
			{
				if (!position.isEmpty())
				{
					position.reset();
				}
			}
		}
	}

	private void calculate()
	{
		if (isCalculated)
			return;
		updateMaxClusterDimension();
		for (int column = 0; column < columnCount; column++)
		{
			for (List<Position> row : graph)
			{
				Position position = row.get(column);
				unitGraph.layout(position);
			}
			unitGraph.nextColumn();
		}
		isCalculated = true;
	}

	private List<Position> getRow(int rowIndex)
	{
		for (int i = graph.size(); i <= rowIndex; i++)
		{
			List<Position> newRow = new ArrayList<Position>();
			for (int j = 0; j < columnCount; j++)
			{
				newRow.add(new Position(i, j));
			}
			graph.add(newRow);
		}
		return graph.get(rowIndex);
	}

	private void addColumn()
	{
		for (int i = 0; i < graph.size(); i++)
		{
			List<Position> row = graph.get(i);
			row.add(new Position(i, columnCount));
		}
		columnCount++;
	}

	private Position getPosition(int rowIndex, int columnIndex)
	{
		List<Position> row = getRow(rowIndex);
		for (int i = columnCount; i <= columnIndex; i++)
		{
			addColumn();
		}
		return row.get(columnIndex);
	}

	private List<Position> getInsertZone(Position anchor)
	{
		List<Position> zone = new ArrayList<Position>();
		if (anchor.rowIndex > 0)
			zone.add(getPosition(anchor.rowIndex - 1, anchor.columnIndex + 1));
		zone.add(getPosition(anchor.rowIndex, anchor.columnIndex + 1));
		zone.add(getPosition(anchor.rowIndex + 1, anchor.columnIndex + 1));
		zone.add(getPosition(anchor.rowIndex + 1, anchor.columnIndex));
		if (anchor.columnIndex > 0)
			zone.add(getPosition(anchor.rowIndex + 1, anchor.columnIndex - 1));
		return zone;
	}

	private Direction getShallowestDirection()
	{
		int maxRightExtent = 0;
		int maxDownExtent = 0;

		for (List<Position> row : graph)
		{
			maxRightExtent = Math.max(maxRightExtent, row.get(row.size() - 1).getRightExtent());
		}
		for (Position position : graph.get(graph.size() - 1))
		{
			maxDownExtent = Math.max(maxDownExtent, position.getDownExtent());
		}

		return (maxRightExtent > maxDownExtent) ? Direction.DOWN : Direction.RIGHT;
	}

	private Position findEmptyPosition()
	{
		for (List<Position> row : graph)
		{
			for (Position position : row)
			{
				if (position.isEmpty())
				{
					return position;
				}
			}
		}

		switch (getShallowestDirection())
		{
			case RIGHT:
				addColumn();
				return getPosition(0, columnCount - 1);
			case DOWN:
				return getRow(graph.size()).get(0);
			default:
				throw new IllegalArgumentException("Unknown direction " + getShallowestDirection());
		}
	}

	private Position findEmptyPositionInInsertionZone(Position anchor)
	{
		for (Position position : getInsertZone(anchor))
		{
			if (position.isEmpty())
			{
				return position;
			}
		}
		return null;
	}

	private void shiftColumnsOverFrom(int columnIndex)
	{
		for (List<Position> row : graph)
		{
			row.add(null);
			for (int i = columnCount; i > columnIndex; i--)
			{
				Position position = row.get(i - 1);
				position.columnIndex = i;
				row.set(i, position);
			}
			row.set(columnIndex, new Position(row.get(0).rowIndex, columnIndex));
		}
		columnCount++;
	}

	private void shiftRowsDownFrom(int rowIndex)
	{
		List<Position> newRow = new ArrayList<Position>();
		for (int i = 0; i < columnCount; i++)
		{
			newRow.add(new Position(rowIndex, i));
		}
		graph.add(rowIndex, newRow);

		for (int i = rowIndex + 1; i < graph.size(); i++)
		{
			List<Position> shiftedRow = graph.get(i);
			for (Position position : shiftedRow)
			{
				position.rowIndex = i;
			}
		}
	}

	private void contractColumnIfEmpty(int columnIndex)
	{
		for (List<Position> row : graph)
		{
			if (!row.get(columnIndex).isEmpty())
				return;
		}

		for (List<Position> row : graph)
		{
			for (int i = columnIndex; i < (columnCount - 1); i++)
			{
				Position position = row.get(i + 1);
				position.columnIndex = i;
				row.set(i, position);
			}
			row.remove(row.size() - 1);
		}

		columnCount--;
	}

	private void contractRowIfEmpty(int rowIndex)
	{
		for (Position position : graph.get(rowIndex))
		{
			if (!position.isEmpty())
				return;
		}

		graph.remove(rowIndex);

		for (int i = rowIndex; i < graph.size(); i++)
		{
			List<Position> row = graph.get(i);
			for (Position position : row)
			{
				position.rowIndex = i;
			}
		}
	}

	private String serialize()
	{
		StringBuilder buffer = new StringBuilder();
		for (List<Position> row : graph)
		{
			buffer.append("{");
			for (Position position : row)
			{
				position.serialize(buffer);
			}
			buffer.append("}");
		}
		return buffer.toString();
	}

	/**
	 * Useful for adjusting the layout graph after the root canvas of a cluster has been deleted.
	 */
	void replaceCluster(long originalRootCanvasId, CIntentionCluster newCluster)
	{
		calculate();

		Position position = positionsByRootCanvasId.get(originalRootCanvasId);
		position.cluster = newCluster;
		positionsByRootCanvasId.put(newCluster.getRootCanvasId(), position);
		reset();
	}

	void insertCluster(CIntentionCluster cluster)
	{
		calculate();

		Position position = findEmptyPosition();
		position.cluster = cluster;
		positionsByRootCanvasId.put(cluster.getRootCanvasId(), position);

		reset();
	}

	void insertCluster(long contextCanvasId, CIntentionCluster cluster)
	{
		calculate();

		Position anchor = positionsByRootCanvasId.get(contextCanvasId);

		if (findEmptyPositionInInsertionZone(anchor) == null)
		{
			switch (getShallowestDirection())
			{
				case RIGHT:
					shiftColumnsOverFrom(anchor.columnIndex + 1);
					break;
				case DOWN:
					shiftRowsDownFrom(anchor.rowIndex + 1);
					break;
			}
		}

		Position position = findEmptyPositionInInsertionZone(anchor);
		position.cluster = cluster;
		positionsByRootCanvasId.put(cluster.getRootCanvasId(), position);

		reset();
	}

	void removeClusterIfAny(long rootCanvasId)
	{
		Position position = positionsByRootCanvasId.remove(rootCanvasId);
		if (position != null)
		{
			position.cluster = null;
			contractRowIfEmpty(position.rowIndex);
			contractColumnIfEmpty(position.columnIndex);

			reset();
		}
	}

	List<CIntentionClusterLayout> layoutClusters()
	{
		calculate();

		List<CIntentionClusterLayout> clusterLayouts = new ArrayList<CIntentionClusterLayout>();

		for (List<Position> row : graph)
		{
			for (Position position : row)
			{
				if (!position.isEmpty())
				{
					position.centerLayoutInUnitBounds();
					clusterLayouts.add(position.clusterLayout);
				}
			}
		}

		return clusterLayouts;
	}

	CalicoPacket createPacket()
	{
		CalicoPacket p = new CalicoPacket();
		p.putInt(IntentionalInterfacesNetworkCommands.CIC_CLUSTER_GRAPH);
		p.putString(serialize());
		return p;
	}

	void inflateStoredData(String data)
	{
		graph.clear();
		positionsByRootCanvasId.clear();

		StringTokenizer rowParser = new StringTokenizer(data, "{}");
		while (rowParser.hasMoreTokens())
		{
			String rowData = rowParser.nextToken();
			List<Position> row = new ArrayList<Position>();
			graph.add(row);

			StringTokenizer positionParser = new StringTokenizer(rowData, "[]");
			while (positionParser.hasMoreTokens())
			{
				String positionData = positionParser.nextToken();
				Position position = new Position(positionData);
				row.add(position);

				if (!position.isEmpty())
				{
					positionsByRootCanvasId.put(position.cluster.getRootCanvasId(), position);
				}
			}
		}
	}
	
	private static Dimension maxClusterDimension = new Dimension(0,0);
	
	public static Dimension getMaxClusterDimension() { return maxClusterDimension; }
	
	private void updateMaxClusterDimension()
	{
		Dimension di 
		for (int column = 0; column < columnCount; column++)
		{
			for (List<Position> row : graph)
			{
				Position position = row.get(column);
				unitGraph.layout(position);
			}
			unitGraph.nextColumn();
		}
	}
}
