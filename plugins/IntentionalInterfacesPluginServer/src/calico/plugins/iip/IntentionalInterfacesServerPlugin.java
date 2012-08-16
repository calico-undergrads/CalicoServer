package calico.plugins.iip;

import java.util.List;

import calico.clients.Client;
import calico.clients.ClientManager;
import calico.controllers.CCanvasController;
import calico.events.CalicoEventHandler;
import calico.events.CalicoEventListener;
import calico.networking.netstuff.CalicoPacket;
import calico.networking.netstuff.NetworkCommand;
import calico.plugins.AbstractCalicoPlugin;
import calico.plugins.CalicoPluginManager;
import calico.plugins.CalicoStateElement;
import calico.plugins.iip.controllers.CCanvasLinkController;
import calico.plugins.iip.controllers.CIntentionCellController;
import calico.plugins.iip.graph.layout.CIntentionClusterLayout;
import calico.plugins.iip.graph.layout.CIntentionLayout;
import calico.uuid.UUIDAllocator;

/**
 * Outermost plugin container.
 * 
 * @author Byron Hawkins
 */
public class IntentionalInterfacesServerPlugin extends AbstractCalicoPlugin implements CalicoEventListener, CalicoStateElement
{
	private final IntentionalInterfaceState state = new IntentionalInterfaceState();

	public IntentionalInterfacesServerPlugin()
	{
		PluginInfo.name = "Intentional Interfaces";
	}

	public void onPluginStart()
	{
		// listen for these events from the main Calico server
		CalicoEventHandler.getInstance().addListener(NetworkCommand.CANVAS_CREATE, this, CalicoEventHandler.PASSIVE_LISTENER);
		CalicoEventHandler.getInstance().addListener(NetworkCommand.CANVAS_DELETE, this, CalicoEventHandler.PASSIVE_LISTENER);
		CalicoEventHandler.getInstance().addListener(NetworkCommand.RESTORE_START, this, CalicoEventHandler.PASSIVE_LISTENER);

		// listen for all the events created by this plugin
		for (Integer event : this.getNetworkCommands())
		{
			System.out.println("IntentionalInterfacesPlugin: attempting to listen for " + event.intValue());
			CalicoEventHandler.getInstance().addListener(event.intValue(), this, CalicoEventHandler.ACTION_PERFORMER_LISTENER);
		}

		// create the default intention types
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "Perspective", 0);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "Alternative", 1);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "Idea", 2);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "Design Inside", 3);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "Continuation", 4);
		CIntentionCellController.getInstance().createIntentionType(UUIDAllocator.getUUID(), "No Tag", 5);

		// plug in to the persistence mechanism
		CalicoPluginManager.registerCalicoStateExtension(this);

		// create a <code>CIntentionCell</code> for each canvas currently existing in the main Calico server (there may
		// be none).
		for (long canvasId : CCanvasController.canvases.keySet())
		{
			createIntentionCell(canvasId);
			CIntentionLayout.getInstance().insertCluster(canvasId);
		}
	}

	@Override
	public void handleCalicoEvent(int event, CalicoPacket p, Client c)
	{
		if (event == NetworkCommand.RESTORE_START)
		{
			clearState();
			return;
		}

		// events from this plugin are handled here
		if (IntentionalInterfacesNetworkCommands.Command.isInDomain(event))
		{
			switch (IntentionalInterfacesNetworkCommands.Command.forId(event))
			{
				case CIC_CREATE:
					CIC_CREATE(p, c);
					break;
				case CIC_MOVE:
					CIC_MOVE(p, c);
					break;
				case CIC_SET_TITLE:
					CIC_SET_TITLE(p, c, true);
					break;
				case CIC_TAG:
					CIC_TAG(p, c);
					break;
				case CIC_UNTAG:
					CIC_UNTAG(p, c, true);
					break;
				case CIC_DELETE:
					CIC_DELETE(p, c);
					break;
				case CIC_CLUSTER_GRAPH:
					CIC_CLUSTER_GRAPH(p, c);
					break;
				case CIT_CREATE:
					CIT_CREATE(p, c);
					break;
				case CIT_RENAME:
					CIT_RENAME(p, c);
					break;
				case CIT_SET_COLOR:
					CIT_SET_COLOR(p, c);
					break;
				case CIT_DELETE:
					CIT_DELETE(p, c);
					break;
				case CLINK_CREATE:
					CLINK_CREATE(p, c);
					break;
				case CLINK_MOVE_ANCHOR:
					CLINK_MOVE_ANCHOR(p, c);
					break;
				case CLINK_LABEL:
					CLINK_LABEL(p, c);
					break;
				case CLINK_DELETE:
					CLINK_DELETE(p, c, true);
					break;
			}
		}
		else
		// events from the main Calico server go here
		{
			p.rewind();
			p.getInt();
			long canvasId = p.getLong();

			switch (event)
			{
				case NetworkCommand.CANVAS_CREATE:
					long originatingCanvasId = p.getLong();
					createIntentionCell(canvasId);
					if (originatingCanvasId > 0L)
					{
						CIntentionLayout.getInstance().insertCluster(originatingCanvasId, canvasId);
					}
					else
					{
						CIntentionLayout.getInstance().insertCluster(canvasId);
					}
					layoutGraph();
					break;
				case NetworkCommand.CANVAS_DELETE:
					CANVAS_DELETE(p, c, canvasId);
					break;
			}
		}
	}

	private static void createIntentionCell(long canvasId)
	{
		CIntentionCell cell = new CIntentionCell(UUIDAllocator.getUUID(), canvasId);
		CIntentionCellController.getInstance().addCell(cell);

		CalicoPacket p = cell.getCreatePacket();
		forward(p);
	}

	private static void clearState()
	{
		CIntentionCellController.getInstance().clearState();
		CCanvasLinkController.getInstance().clearState();
	}

	// this is called only during restore
	private static void CIC_CREATE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_CREATE.verify(p);

		long uuid = p.getLong();
		long canvasId = p.getLong();
		int x = p.getInt();
		int y = p.getInt();
		String title = p.getString();

		CIntentionCell cell = new CIntentionCell(uuid, canvasId);
		cell.setLocation(x, y);
		cell.setTitle(title);

		CIntentionCellController.getInstance().addCell(cell);
		// clusters will be restored in CIntentionClusterGraph.inflateStoredData()
	}

	private static void CANVAS_DELETE(CalicoPacket p, Client c, long canvasId)
	{
		CIntentionCell cell = CIntentionCellController.getInstance().getCellByCanvasId(canvasId);
		CIntentionCellController.getInstance().removeCellById(cell.getId());
		deleteAllLinks(canvasId, true); // also removes the cluster, if `canvasId represented a cluster root

		CalicoPacket cicDelete = CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIC_DELETE, cell.getId());
		forward(cicDelete);

		layoutGraph();
	}

	private static void deleteAllLinks(long canvasId, boolean forward)
	{
		long rootCanvasId = CIntentionLayout.getInstance().getRootCanvasId(canvasId);
		List<Long> linkIds = CCanvasLinkController.getInstance().getLinkIdsForCanvas(canvasId);
		if (linkIds.isEmpty())
		{
			CIntentionLayout.getInstance().removeClusterIfAny(canvasId);
			return;
		}

		if (canvasId == rootCanvasId)
		{ // assign the first linked canvas to take the place of the deleted cluster root
			CCanvasLink firstDeletedLink = deleteLink(linkIds.get(0), forward);
			long assignedCanvasContext = firstDeletedLink.getAnchorB().getCanvasId();
			CIntentionLayout.getInstance().replaceCluster(canvasId, assignedCanvasContext);

			for (int i = 1; i < linkIds.size(); i++)
			{ // create a new cluster for each other canvas that was linked from `canvasId
				CCanvasLink deletedLink = deleteLink(linkIds.get(i), forward);
				CIntentionLayout.getInstance().insertCluster(deletedLink.getAnchorA().getCanvasId(), deletedLink.getAnchorB().getCanvasId());
			}
		}
		else
		{
			long incomingLinkId = CCanvasLinkController.getInstance().getIncomingLink(canvasId);
			deleteLink(incomingLinkId, forward);
			linkIds.remove(incomingLinkId);

			for (Long linkId : linkIds)
			{// create a new cluster for each canvas that was linked from `canvasId
				CCanvasLink deletedLink = deleteLink(linkId, forward);
				CIntentionLayout.getInstance().insertCluster(rootCanvasId, deletedLink.getAnchorB().getCanvasId());
			}
		}
	}

	private static CCanvasLink deleteLink(long linkId, boolean forward)
	{
		if (forward)
		{
			CalicoPacket packet = new CalicoPacket();
			packet.putInt(IntentionalInterfacesNetworkCommands.CLINK_DELETE);
			packet.putLong(linkId);
			forward(packet);
		}

		return CCanvasLinkController.getInstance().removeLinkById(linkId);
	}

	private static void CIC_MOVE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_MOVE.verify(p);

		long uuid = p.getLong();
		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);

		int x = p.getInt();
		int y = p.getInt();
		cell.setLocation(x, y);

		forward(p, c);
	}

	private static void CIC_SET_TITLE(CalicoPacket p, Client c, boolean forward)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_SET_TITLE.verify(p);

		long uuid = p.getLong();
		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);

		cell.setTitle(p.getString());

		if (forward)
		{
			forward(p, c);
		}
	}

	private static void CIC_TAG(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_TAG.verify(p);

		long uuid = p.getLong();
		long typeId = p.getLong();

		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);
		cell.setIntentionType(typeId);

		forward(p, c);
	}

	private static void CIC_UNTAG(CalicoPacket p, Client c, boolean forward)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_UNTAG.verify(p);

		long uuid = p.getLong();
		long typeId = p.getLong();

		CIntentionCell cell = CIntentionCellController.getInstance().getCellById(uuid);
		cell.clearIntentionType();

		if (forward)
		{
			forward(p, c);
		}
	}

	private static void CIC_DELETE(CalicoPacket p, Client c)
	{
		throw new UnsupportedOperationException("It is no longer allowed to delete a CIC separately from its CCanvas.");
	}

	private static void CIC_CLUSTER_GRAPH(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIC_CLUSTER_GRAPH.verify(p);

		CIntentionLayout.getInstance().inflateStoredClusterGraph(p.getString());
	}

	/**
	 * Create a new CIntentionType, possibly with a color index already assigned. If no color index is assigned, the
	 * server will choose a color randomly.
	 */
	private static void CIT_CREATE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_CREATE.verify(p);

		long uuid = p.getLong();
		String name = p.getString();
		int colorIndex = p.getInt();

		CIntentionType type = CIntentionCellController.getInstance().createIntentionType(uuid, name, colorIndex);

		CalicoPacket colored = CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIT_CREATE, uuid, name, type.getColorIndex());
		ClientManager.send(colored);
	}

	private static void CIT_RENAME(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_RENAME.verify(p);

		long uuid = p.getLong();
		String name = p.getString();
		CIntentionCellController.getInstance().renameIntentionType(uuid, name);

		forward(p, c);
	}

	private static void CIT_SET_COLOR(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_SET_COLOR.verify(p);

		long uuid = p.getLong();
		int color = p.getInt();
		CIntentionCellController.getInstance().setIntentionTypeColor(uuid, color);

		forward(p, c);
	}

	private static void CIT_DELETE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CIT_DELETE.verify(p);

		long uuid = p.getLong();

		CIntentionCellController.getInstance().removeIntentionType(uuid);

		forward(p, c);
	}

	private static CCanvasLinkAnchor unpackAnchor(long link_uuid, CalicoPacket p)
	{
		long uuid = p.getLong();
		long canvas_uuid = p.getLong();
		CCanvasLinkAnchor.Type type = CCanvasLinkAnchor.Type.values()[p.getInt()];
		int x = p.getInt();
		int y = p.getInt();
		long group_uuid = p.getLong();
		return new CCanvasLinkAnchor(uuid, link_uuid, canvas_uuid, type, x, y, group_uuid);
	}

	private static void CLINK_CREATE(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_CREATE.verify(p);

		long uuid = p.getLong();
		CCanvasLinkAnchor anchorA = unpackAnchor(uuid, p);
		CCanvasLinkAnchor anchorB = unpackAnchor(uuid, p);

		if (!(CCanvasController.canvases.containsKey(anchorA.getCanvasId()) && CCanvasController.canvases.containsKey(anchorB.getCanvasId())))
		{
			// the canvas has been deleted
			return;
		}

		Long incomingLinkId = CCanvasLinkController.getInstance().getIncomingLink(anchorB.getCanvasId());
		if (incomingLinkId == null)
		{ // the canvas is not linked, so it must be a cluster root, which means that entire cluster will be attached to
		  // the <code>anchorA</code> cluster. So remove the cluster instance from the layout.
			CIntentionLayout.getInstance().removeClusterIfAny(anchorB.getCanvasId());
		}
		else
		{ // the canvas is linked already, so steal it
			CCanvasLinkController.getInstance().removeLinkById(incomingLinkId);
			CalicoPacket deleteIncoming = CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CLINK_DELETE, incomingLinkId);
			forward(deleteIncoming);
		}

		CCanvasLink link = new CCanvasLink(uuid, anchorA, anchorB);
		CCanvasLinkController.getInstance().addLink(link);

		layoutGraph();

		forward(p, c);
	}

	/**
	 * Move the position of a link's anchor point. This method is also designated for changing the canvases to which the
	 * link is attached, though that behavior is not presently supported.
	 */
	private static void CLINK_MOVE_ANCHOR(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_MOVE_ANCHOR.verify(p);

		long anchor_uuid = p.getLong();
		long canvas_uuid = p.getLong();
		CCanvasLinkAnchor.Type type = CCanvasLinkAnchor.Type.values()[p.getInt()];
		int x = p.getInt();
		int y = p.getInt();

		CCanvasLinkController.getInstance().moveLinkAnchor(anchor_uuid, canvas_uuid, type, x, y);

		forward(p, c);
	}

	private static void CLINK_LABEL(CalicoPacket p, Client c)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_LABEL.verify(p);

		long uuid = p.getLong();
		CCanvasLink link = CCanvasLinkController.getInstance().getLinkById(uuid);
		link.setLabel(p.getString());

		forward(p, c);
	}

	private static void CLINK_DELETE(CalicoPacket p, Client c, boolean forward)
	{
		p.rewind();
		IntentionalInterfacesNetworkCommands.Command.CLINK_DELETE.verify(p);

		long uuid = p.getLong();
		CCanvasLink deletedLink = CCanvasLinkController.getInstance().removeLinkById(uuid);
		CIntentionLayout.getInstance().insertCluster(deletedLink.getAnchorA().getCanvasId(), deletedLink.getAnchorB().getCanvasId());

		layoutGraph();

		if (forward)
		{
			forward(p, c);
		}
	}

	/**
	 * Invoke the cluster layout. For each canvas, assign its new location. If this step actually moves the canvas, send
	 * a packet with the new coordinates to all clients. Otherwise continue as if the canvas did not move.
	 */
	private static void layoutGraph()
	{
		List<CIntentionClusterLayout> clusterLayouts = CIntentionLayout.getInstance().layoutGraph();

		for (CIntentionClusterLayout clusterLayout : clusterLayouts)
		{
			for (CIntentionClusterLayout.CanvasPosition canvas : clusterLayout.getCanvasPositions())
			{
				CIntentionCell cell = CIntentionCellController.getInstance().getCellByCanvasId(canvas.canvasId);
				if (cell.setLocation(canvas.location.x, canvas.location.y))
				{ // <code>setLocation()</code> returns true when the canvas moves
					CalicoPacket p = new CalicoPacket();
					p.putInt(IntentionalInterfacesNetworkCommands.CIC_MOVE);
					p.putLong(cell.getId());
					p.putInt(cell.getLocation().x);
					p.putInt(cell.getLocation().y);
					forward(p);
				}
			}
		}

		forward(CIntentionLayout.getInstance().getTopology().createPacket());
	}

	/**
	 * Forward <code>p</code> to all clients.
	 */
	private static void forward(CalicoPacket p)
	{
		forward(p, null);
	}

	/**
	 * Forward <code>p</code> to all clients except <code>c</code> (null is tolerated for <code>c</code>).
	 */
	private static void forward(CalicoPacket p, Client c)
	{
		if (c == null)
		{
			ClientManager.send(p);
		}
		else
		{
			ClientManager.send_except(c, p);
		}
	}

	@Override
	public CalicoPacket[] getCalicoStateElementUpdatePackets()
	{
		state.reset();
		CIntentionCellController.getInstance().populateState(state);
		CCanvasLinkController.getInstance().populateState(state);
		CIntentionLayout.getInstance().populateState(state);

		return state.getAllPackets();
	}

	public Class<?> getNetworkCommandsClass()
	{
		return IntentionalInterfacesNetworkCommands.class;
	}
}