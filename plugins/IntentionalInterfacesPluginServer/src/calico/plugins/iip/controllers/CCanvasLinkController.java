package calico.plugins.iip.controllers;

import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import calico.plugins.iip.CCanvasLink;
import calico.plugins.iip.CCanvasLinkAnchor;
import calico.plugins.iip.IntentionalInterfaceState;
import calico.plugins.iip.IntentionalInterfacesServerPlugin;
import calico.plugins.iip.graph.layout.CIntentionLayout;

/**
 * Coordinates instances of <code>CCanvasLink</code> and <code>CCanvasLinkAnchor</code> with commands received from
 * clients.
 * 
 * @author Byron Hawkins
 */
public class CCanvasLinkController
{
	public static CCanvasLinkController getInstance()
	{
		return INSTANCE;
	}

	private static final CCanvasLinkController INSTANCE = new CCanvasLinkController();

	/**
	 * All links in the IntentionView.
	 */
	private static Long2ReferenceArrayMap<CCanvasLink> links = new Long2ReferenceArrayMap<CCanvasLink>();
	/**
	 * All link anchors in the IntentionView.
	 */
	private static Long2ReferenceArrayMap<CCanvasLinkAnchor> linkAnchors = new Long2ReferenceArrayMap<CCanvasLinkAnchor>();
	/**
	 * All link anchors in the IntentionView, grouped by the canvas they are attached to, and indexed by the canvas id.
	 */
	private static Long2ReferenceArrayMap<Set<Long>> anchorIdsByCanvasId = new Long2ReferenceArrayMap<Set<Long>>();

	public void populateState(IntentionalInterfaceState state)
	{
		for (CCanvasLink link : links.values())
		{
			state.addLinkPacket(link.getState());
		}
	}

	public void clearState()
	{
		links.clear();
		linkAnchors.clear();
		anchorIdsByCanvasId.clear();
	}

	public CCanvasLinkAnchor getAnchor(long anchorId)
	{
		return linkAnchors.get(anchorId);
	}

	public CCanvasLink getLink(long linkId)
	{
		return links.get(linkId);
	}

	/**
	 * Get the id of the unique link coming into <code>canvasId</code>, or <code>null</code> if there is none.
	 */
	public Long getIncomingLink(long canvasId)
	{
		Set<Long> anchorIds = anchorIdsByCanvasId.get(canvasId);
		if (anchorIds == null)
		{
			return null;
		}

		for (Long anchorId : anchorIdsByCanvasId.get(canvasId))
		{
			if (isDestination(anchorId))
			{
				return linkAnchors.get(anchorId).getLinkId();
			}
		}
		return null;
	}

	/**
	 * Get the anchor on the other side of the link from <code>anchorId</code>.
	 */
	public CCanvasLinkAnchor getOpposite(long anchorId)
	{
		CCanvasLinkAnchor anchor = linkAnchors.get(anchorId);
		CCanvasLink link = links.get(anchor.getLinkId());
		if (link.getAnchorA() == anchor)
		{
			return link.getAnchorB();
		}
		else
		{
			return link.getAnchorA();
		}
	}

	/**
	 * Return true if <code>anchorId</code> represents an arrowhead.
	 */
	public boolean isDestination(long anchorId)
	{
		CCanvasLinkAnchor anchor = linkAnchors.get(anchorId);
		CCanvasLink link = links.get(anchor.getLinkId());
		return (link.getAnchorB() == anchor);
	}

	/**
	 * Return true if <code>anchorId</code> represents an arrowhead which is presently attached to a canvas.
	 */
	public boolean isConnectedDestination(long anchorId)
	{
		if (!isDestination(anchorId))
		{
			return false;
		}

		return getOpposite(anchorId).getCanvasId() >= 0;
	}

	public void addLink(CCanvasLink link)
	{
		links.put(link.getId(), link);

		addLinkAnchor(link.getAnchorA());
		addLinkAnchor(link.getAnchorB());
	}

	private void addLinkAnchor(CCanvasLinkAnchor anchor)
	{
		linkAnchors.put(anchor.getId(), anchor);
		getAnchorIdsForCanvasId(anchor.getCanvasId()).add(anchor.getId());
	}

	public CCanvasLink getLinkById(long uuid)
	{
		return links.get(uuid);
	}

	public CCanvasLink removeLinkById(long uuid)
	{
		CCanvasLink link = links.remove(uuid);
		removeLinkAnchor(link.getAnchorA());
		removeLinkAnchor(link.getAnchorB());
		return link;
	}

	private void removeLinkAnchor(CCanvasLinkAnchor anchor)
	{
		linkAnchors.remove(anchor.getId());
		getAnchorIdsForCanvasId(anchor.getCanvasId()).remove(anchor.getId());
	}

	/**
	 * Get the ids of all the anchors attached to <code>canvasId</code>.
	 */
	public Set<Long> getAnchorIdsForCanvasId(long canvasId)
	{
		Set<Long> anchorIds = anchorIdsByCanvasId.get(canvasId);
		if (anchorIds == null)
		{
			anchorIds = new HashSet<Long>();
			anchorIdsByCanvasId.put(canvasId, anchorIds);
		}
		return anchorIds;
	}

	/**
	 * Change the pixel position of a link anchor. This method is also designated for changing the canvas to which the
	 * anchor is attached, though this behavior is presently not supported.
	 */
	public void moveLinkAnchor(long anchor_uuid, long canvas_uuid, CCanvasLinkAnchor.Type type, int x, int y)
	{
		CCanvasLinkAnchor anchor = linkAnchors.get(anchor_uuid);
		boolean changedCanvas = (canvas_uuid != anchor.getCanvasId());
		if (changedCanvas)
		{
			throw new UnsupportedOperationException("Moving arrows from one canvas to another is not presently supported.");
		}
		anchor.move(canvas_uuid, type, x, y);
	}

	/**
	 * Get the ids of all the links attached to <code>canvasId</code>.
	 */
	public List<Long> getLinkIdsForCanvas(long canvasId)
	{
		List<Long> linkIds = new ArrayList<Long>();
		for (Long anchorId : getAnchorIdsForCanvasId(canvasId))
		{
			linkIds.add(linkAnchors.get(anchorId).getLinkId());
		}
		return linkIds;
	}
}
