package calico.plugins.iip;

import java.awt.Point;
import java.util.ArrayList;
import java.util.List;

import calico.components.CCanvas;
import calico.networking.netstuff.CalicoPacket;
import calico.plugins.iip.graph.layout.CIntentionLayout;

/**
 * Represents one canvas thumbnail in the Intention View. Internally it maintains a pixel position, a title, and the id
 * of its <code>CIntentionType</code> (if any).
 * 
 * @author Byron Hawkins
 */
public class CIntentionCell
{
	public static final String DEFAULT_TITLE = "<default>";

	private long uuid;
	private long canvas_uuid;
	private final Point location;
	private String title;
	private Long intentionTypeId = null;

	public CIntentionCell(long uuid, long canvasId)
	{
		this.uuid = uuid;
		this.canvas_uuid = canvasId;
		this.location = new Point(-(CIntentionLayout.INTENTION_CELL_SIZE.width / 2), -(CIntentionLayout.INTENTION_CELL_SIZE.height / 2));
		this.title = DEFAULT_TITLE;
	}

	public long getId()
	{
		return uuid;
	}

	public long getCanvasId()
	{
		return canvas_uuid;
	}

	public Point getLocation()
	{
		return location;
	}

	/**
	 * If different than the current location, set the location of the CIC and return true; otherwise return false to
	 * indicate that the position did not change.
	 */
	public boolean setLocation(int x, int y)
	{
		if ((location.x == x) && (location.y == y))
		{
			return false;
		}

		location.x = x;
		location.y = y;

		return true;
	}

	public String getTitle()
	{
		return title;
	}

	/**
	 * Return true if the user has assigned a title to this CIC; otherwise return false, indicating that this CIC has
	 * the default title.
	 */
	public boolean hasUserTitle()
	{
		return !title.equals(DEFAULT_TITLE);
	}

	public void setTitle(String title)
	{
		this.title = title;
	}

	/**
	 * Return true if any tag is assigned to this CIC, or false if the tag is null.
	 */
	public boolean hasIntentionType()
	{
		return (intentionTypeId != null);
	}

	public Long getIntentionTypeId()
	{
		return intentionTypeId;
	}

	public void setIntentionType(long intentionTypeId)
	{
		this.intentionTypeId = intentionTypeId;
	}

	/**
	 * Set the tag back to <code>null</code>.
	 */
	public void clearIntentionType()
	{
		intentionTypeId = null;
	}

	public CalicoPacket getCreatePacket()
	{
		return CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIC_CREATE, uuid, canvas_uuid, location.x, location.y, title);
	}

	public void populateState(IntentionalInterfaceState state)
	{
		state.addCellPacket(getCreatePacket());

		if (intentionTypeId != null)
		{
			state.addCellPacket(CalicoPacket.getPacket(IntentionalInterfacesNetworkCommands.CIC_TAG, uuid, intentionTypeId));
		}
	}
}
