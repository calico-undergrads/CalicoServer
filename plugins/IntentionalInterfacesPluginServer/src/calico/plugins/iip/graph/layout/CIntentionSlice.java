package calico.plugins.iip.graph.layout;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Maintains the layout details for one slice of a cluster. Each slice is defined by its root canvas, which always has
 * one arrow from the cluster's root canvas (so the slices for a cluster are defined by the set of arrows from the
 * cluster's root canvas). The set of canvases contained by a cluster is the set of canvases transitively reachable from
 * the slice's root canvas along outgoing arrows.
 * 
 * A slice exists for only one iteration of the layout. The next iteration will create a new set of slices, and at that
 * point this slice will be discarded.
 * 
 * @author Byron Hawkins
 */
public class CIntentionSlice
{
	// for debug
	private static final DecimalFormat WEIGHT_FORMAT = new DecimalFormat("0.00");

	/**
	 * Identifies the root canvas of the slice
	 */
	private final long rootCanvasId;
	/**
	 * Identifies the set of canvases in this slice.
	 */
	private final List<Long> canvasIds = new ArrayList<Long>();
	/**
	 * A slice occupies a segment of each ring in the containing cluster layout. These "ring segments" are referred to
	 * as "arcs" and are maintained in this collection of <code>Arc</code> structures.
	 */
	private final List<Arc> arcs = new ArrayList<Arc>();
	/**
	 * Pixel position of the center of each canvas on its arc, stored as a mapping [canvas id -> counter-clockwise pixel
	 * count] (starting from the clockwise endpoint of the arc).
	 */
	private final Map<Long, Integer> arcPositions = new HashMap<Long, Integer>();

	/**
	 * Weight of the most populous arc, based on arc weights which are calculated externally and assigned.
	 */
	private double maxArcWeight;
	/**
	 * Actual operating weight of this slice, as adjusted by an external calculation and assigned to this slice.
	 */
	private double assignedWeight;
	/**
	 * Transitory value (refreshed each time <code>layoutArc</code> is called) which contains the length (in pixels) of
	 * the last arc to be laid out.
	 */
	private int layoutSpan;

	CIntentionSlice(long rootCanvasId)
	{
		this.rootCanvasId = rootCanvasId;
	}

	long getRootCanvasId()
	{
		return rootCanvasId;
	}

	void addCanvas(long parentCanvasId, long canvasId, int ringIndex)
	{
		canvasIds.add(canvasId);
		getArc(ringIndex).addCanvas(parentCanvasId, canvasId);
	}

	int getLayoutSpan()
	{
		return layoutSpan;
	}

	/**
	 * Return the quantity of canvases in this slice.
	 */
	int size()
	{
		return canvasIds.size();
	}

	/**
	 * Return the quantity of canvases in the arc occupying <code>ringIndex</code for this slice.
	 */
	int arcSize(int ringIndex)
	{
		return getArc(ringIndex).canvasCount;
	}

	public double getWeight()
	{
		return assignedWeight;
	}

	/**
	 * Assign a relative weight to this slice, where <code>0 <= weight <= 1.0</code>.
	 */
	void setWeight(double weight)
	{
		assignedWeight = weight;

		System.out.println(String.format("Slice for canvas %d has %d canvases and max weight %s with normalized weight %s%%",
				CIntentionLayout.getCanvasIndex(rootCanvasId), canvasIds.size(), WEIGHT_FORMAT.format(getMaxArcWeight()), toPercent(assignedWeight)));

		for (Arc arc : arcs)
		{
			arc.calculateArcSpanProjection();

			System.out.println(String.format("Slice for canvas %d has projected span %d for ring %d", CIntentionLayout.getCanvasIndex(rootCanvasId),
					arc.arcSpanProjection, arc.ringIndex));
		}
	}

	void setArcWeight(int ringIndex, double weight)
	{
		getArc(ringIndex).setWeight(weight);
	}

	/**
	 * Identify the heaviest arc in this slice and store its weight in the transitory field
	 * <code>this.maxArcWeight</code>.
	 */
	void calculateMaxArcWeight()
	{
		maxArcWeight = 0.0;
		for (Arc arc : arcs)
		{
			if (arc.weight > maxArcWeight)
			{
				maxArcWeight = arc.weight;
			}
		}
	}

	double getMaxArcWeight()
	{
		return maxArcWeight;
	}

	int getMaxProjectedSpan(int ringIndex)
	{
		return getArc(ringIndex).arcSpanProjection;
	}

	/**
	 * Calculate the number of pixels that will be occupied by this slice in a ring of pixel length
	 * <code>ringSpan</code>.
	 */
	int calculateLayoutSpan(int ringSpan)
	{
		return (int) (ringSpan * assignedWeight);
	}

	/**
	 * Layout one arc of this slice. The goal of this algorithm is to make it appear that each <code>CanvasGroup</code>
	 * is placed directly "in front" of its parent canvas (in the hierarchy defined by intention arrows).
	 * 
	 * @param arcTransformer
	 *            a trig transform utility that is preconfigured to translate a linear pixel position into the
	 *            corresponding (x,y) position along the ring identified by <code>ringIndex</code>.
	 * @param ringIndex
	 *            identifies the arc to be laid out
	 * @param ringSpan
	 *            specifies the pixel length of the whole ring
	 * @param arcStart
	 *            specifies the leftmost position which may be occupied by a canvas of this arc
	 * @param layout
	 *            canvas positions are assigned to this instance
	 * @param parentRingRadius
	 *            radius of the next smallest ring
	 */
	void layoutArc(CIntentionArcTransformer arcTransformer, int ringIndex, int ringSpan, int arcStart, CIntentionClusterLayout layout, Double parentRingRadius)
	{
		final int sliceWidth = calculateLayoutSpan(ringSpan);

		Arc arc = arcs.get(ringIndex);
		if (!arc.isEmpty())
		{
			// collection of all sets of collisions (see <code>GroupCollision</code> for semantic details)
			List<GroupCollision> calculatedCollisions = new ArrayList<GroupCollision>();
			if (parentRingRadius != null)
			{
				// collection of all collisions which (transitively) affect the current <code>group</code>
				List<GroupCollision> collisionsInEffect = new ArrayList<GroupCollision>();

				double leftBoundary = arcStart; // maintains the actual left boundary of placed canvases
				CanvasGroup previousGroup = null;
				for (CanvasGroup group : arc.canvasGroups.values())
				{
					group.idealPosition = arcTransformer.calculateIdealPosition(arcPositions.get(group.parentCanvasId), parentRingRadius);

					System.out.println("Ideal position for group of arc " + ringIndex + " in slice for canvas " + CIntentionLayout.getCanvasIndex(rootCanvasId)
							+ ": " + group.idealPosition + " in (" + arcStart + " - " + (arcStart + sliceWidth) + ")");

					// the <code>group.idealPosition</code> refers to the middle of the group, so find the corresponding
					// left edge
					double idealStart = group.idealPosition - (group.getSpan() / 2.0);

					// this code keeps more information about collisions than is currently being used. A GroupCollision
					// is only removed from <code>collisionsInEffect</code> when its entire chain of displacements has
					// no effect on the current <code>group</code>. The current layout only needs to know about the very
					// last <code>GroupCollision</code>, i.e., there only needs to be one element in
					// <code>collisionsInEffect</code>. If this algorithm ever needs to be more sophisticated about
					// making the collision adjustments look natural, this extra information may be useful.
					for (int i = (collisionsInEffect.size() - 1); i >= 0; i--)
					{
						GroupCollision collision = collisionsInEffect.get(i);
						if (collision.currentLeftBoundary > idealStart)
						{
							collision.displace(group, collision.currentLeftBoundary - idealStart);
							collision.currentLeftBoundary += group.getSpan();
						}
						else
						{
							collisionsInEffect.remove(i);
							calculatedCollisions.add(collision);
						}
					}

					if (idealStart < leftBoundary)
					{ 
						if (leftBoundary != arcStart)
						{ // this <code>group</code> is now in collision, so create a <code>GroupCollision</code> for it.
							GroupCollision collision = new GroupCollision(previousGroup);
							collision.displace(group, (leftBoundary - idealStart));
							collision.currentLeftBoundary = leftBoundary + group.getSpan();
							collisionsInEffect.add(collision);
						}

						// idealism meets reality :-)
						idealStart = leftBoundary;
					}

					leftBoundary = idealStart + group.getSpan();
					previousGroup = group;
				}

				// reset the collision status for the next iteration
				calculatedCollisions.addAll(collisionsInEffect);
				collisionsInEffect.clear();

				// debug
				for (GroupCollision collision : calculatedCollisions)
				{
					collision.describe();
				}
			}

			Map<CanvasGroup, Integer> displacements = new HashMap<CanvasGroup, Integer>();
			if (parentRingRadius == null)
			{ // no displacements for the slice root
				displacements = null;
			}
			else
			{
				for (GroupCollision collision : calculatedCollisions)
				{
					if (collision.displacements.size() > 1)
					{ // too many collisions--skip displacing and just crowd all groups toward the middle of the arc
						displacements = null;
						break;
					}

					Displacement displacement = collision.displacements.get(0);
					displacements.put(displacement.displacedGroup, (int) displacement.displacementSpan);
				}
			}

			// start xArc in the middle of the arc. IF ideal positions and displacements are in effect, they will change
			// xArc accordingly.
			final int arcOccupancySpan = (arc.canvasCount - 1) * CIntentionLayout.INTENTION_CELL_DIAMETER;
			int xArc = arcStart + ((sliceWidth - arcOccupancySpan) / 2);

			for (CanvasGroup group : arc.canvasGroups.values())
			{
				if (displacements != null)
				{ // use the ideal positions and displacements
					xArc = (int) (group.idealPosition + (CIntentionLayout.INTENTION_CELL_DIAMETER / 2.0) - (group.getSpan() / 2.0));

					Integer displacement = displacements.get(group);
					if (displacement != null)
					{
						xArc += displacement;
					}
				}

				// for each canvas in the group (from left to right), place it in a compact segment starting at
				// <code>xArc</code>.
				for (Long canvasId : group.groupCanvasIds)
				{
					layout.addCanvas(canvasId, arcTransformer.centerCanvasAt(xArc));
					arcPositions.put(canvasId, xArc);
					xArc += CIntentionLayout.INTENTION_CELL_DIAMETER;
				}
			}
		}

		layoutSpan = sliceWidth;
	}

	/**
	 * Get a lazily constructed arc for this slice to occupy <code>ringIndex</code>.
	 */
	private Arc getArc(int ringIndex)
	{
		for (int i = arcs.size(); i <= ringIndex; i++)
		{
			arcs.add(new Arc(i));
		}
		return arcs.get(ringIndex);
	}

	/**
	 * Maintains layout structure per arc.
	 * 
	 * @author Byron Hawkins
	 */
	private class Arc
	{
		/**
		 * The ring occupied by this arc.
		 */
		private int ringIndex;
		/**
		 * The set of canvas sibling groups, indexed by the group parent, where the hierarchy is defined by the set of
		 * intention arrows.
		 */
		private final Map<Long, CanvasGroup> canvasGroups = new LinkedHashMap<Long, CanvasGroup>();
		/**
		 * quantity of canvases in this arc.
		 */
		int canvasCount = 0;
		/**
		 * Weight of this arc relative to other arcs on the same ring (calculated and assigned externally).
		 */
		private double weight;
		/**
		 * This arc has a weight and also has a minimum span in which its canvases will fit. So the
		 * <code>arcSpanProjection</code> specifies exactly how large the ring needs to be for this arc to fit neatly
		 * within the span proportional to its weight.
		 * 
		 * For example, suppose this arc has 3 canvases requiring a total of 240 linear pixesls, and also has weight
		 * 0.25. For a ring of 960 linear pixels, this arc will fit exactly within its weight-allocated span, which is
		 * 960 * 0.25 = 240. So the <code>arcSpanProjection</code> basically says, 'Given that my minimum size is 240
		 * linear pixels and my weight is 0.25, please make the ring at least 960 linear pixels, or I won't fit.'
		 */
		private int arcSpanProjection;

		Arc(int ringIndex)
		{
			this.ringIndex = ringIndex;
		}

		void addCanvas(long parentCanvasId, long canvasId)
		{
			canvasCount++;
			getCanvasGroup(parentCanvasId).addCanvas(canvasId);
		}

		boolean isEmpty()
		{
			return canvasGroups.isEmpty();
		}

		void setWeight(double weight)
		{
			this.weight = weight;
		}

		void calculateArcSpanProjection()
		{
			arcSpanProjection = (int) ((canvasCount * CIntentionLayout.INTENTION_CELL_DIAMETER) * (1.0 / assignedWeight));
		}

		/**
		 * Get a lazily constructed canvas group.
		 */
		private CanvasGroup getCanvasGroup(long parentCanvasId)
		{
			CanvasGroup group = canvasGroups.get(parentCanvasId);
			if (group == null)
			{
				group = new CanvasGroup(parentCanvasId);
				canvasGroups.put(parentCanvasId, group);
			}
			return group;
		}
	}

	/**
	 * During the <code>layoutArc()</code> method, groups are placed along the arc from left to right (here regarding an
	 * arc to have only 1 dimension). If a group's ideal position causes a collision with the previous (leftward) group,
	 * then a new GroupCollision is created to mitigate the necessary adjustment. The <code>ideallyPlacedGroup</code>
	 * refers to the last group which was placed at its ideal position. For each group after the
	 * <code>ideallyPlacedGroup</code>, if it collides with its previous (leftward) neighbor, then it will be added to
	 * <code>displacements</code>. When a group is encountered which does not collide with its previous (leftward)
	 * neighbor, the assembly of the <code>GroupCollision</code> becomes complete; no more groups will ever be added to
	 * that instance of <code>GroupCollision</code>.
	 * 
	 * The purpose of this object is to allow <code>layoutArc()</code> to collect all the group collisions and then stop
	 * its work for a moment to think about how to compensate for them. Sometimes it will simply bump the groups over a
	 * minimum number of pixels such that collisions are avoided. In other cases it will "punt" on the ideal positioning
	 * and simply distribute the groups evenly across the arc. The net effect is that the slices always appear to
	 * proceed outward from the cluster root in a straight line. Even when arcs become crowded and adjustments are made,
	 * it always generally looks like each slice radiates outward in a "logically straight" path.
	 * 
	 * @author Byron Hawkins
	 */
	private class GroupCollision
	{
		private final CanvasGroup ideallyPlacedGroup;
		private final List<Displacement> displacements = new ArrayList<Displacement>();

		// transitory during computation
		private double currentLeftBoundary;

		GroupCollision(CanvasGroup ideallyPlacedGroup)
		{
			this.ideallyPlacedGroup = ideallyPlacedGroup;
		}

		void displace(CanvasGroup group, double span)
		{
			displacements.add(new Displacement(group, span));
		}

		void describe()
		{
			double totalSpan = 0.0;
			for (Displacement displacement : displacements)
			{
				totalSpan += displacement.displacementSpan;
			}

			System.out.println("Collision for group with parent " + CIntentionLayout.getCanvasIndex(ideallyPlacedGroup.parentCanvasId) + ": "
					+ displacements.size() + " displacements totaling " + ((int) totalSpan) + " arc pixels.");
		}
	}

	private class Displacement
	{
		private final CanvasGroup displacedGroup;
		private final double displacementSpan;

		Displacement(CanvasGroup displacedGroup, double displacementSpan)
		{
			this.displacedGroup = displacedGroup;
			this.displacementSpan = displacementSpan;
		}
	}

	/**
	 * Container for a set of canvases which have a common parent in the hierarchy defined by intention arrows. Canvases
	 * in a group always share the same arc, as specified by the layout structure.
	 * 
	 * @author Byron Hawkins
	 */
	private class CanvasGroup
	{
		private final long parentCanvasId;
		private final List<Long> groupCanvasIds = new ArrayList<Long>();

		// transitory
		double idealPosition;

		CanvasGroup(long parentCanvasId)
		{
			this.parentCanvasId = parentCanvasId;
		}

		void addCanvas(long canvasId)
		{
			groupCanvasIds.add(canvasId);
		}

		/**
		 * Return the pixel length that will be occupied by this group when it is placed on an arc.
		 */
		int getSpan()
		{
			return groupCanvasIds.size() * CIntentionLayout.INTENTION_CELL_DIAMETER;
		}
	}

	// debug utility
	private static String toPercent(double d)
	{
		return String.valueOf((int) (d * 100.0));
	}
}
