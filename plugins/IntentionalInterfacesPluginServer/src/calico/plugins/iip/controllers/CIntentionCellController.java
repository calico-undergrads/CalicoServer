package calico.plugins.iip.controllers;

import it.unimi.dsi.fastutil.longs.Long2ReferenceArrayMap;

import java.util.Arrays;
import java.util.Collection;

import calico.plugins.iip.CIntentionCell;
import calico.plugins.iip.CIntentionType;
import calico.plugins.iip.IntentionalInterfaceState;

/**
 * Coordinates all instances of <code>CIntentionCell</code> with commands from clients.
 * 
 * @author Byron Hawkins
 */
public class CIntentionCellController
{
	public static CIntentionCellController getInstance()
	{
		return INSTANCE;
	}

	private static final CIntentionCellController INSTANCE = new CIntentionCellController();

	/**
	 * Contains the current set of <code>CIntentionType</code>s which are available for tagging canvases.
	 */
	private static Long2ReferenceArrayMap<CIntentionType> activeIntentionTypes = new Long2ReferenceArrayMap<CIntentionType>();

	/**
	 * All CICs in the IntentionView.
	 */
	private static Long2ReferenceArrayMap<CIntentionCell> cells = new Long2ReferenceArrayMap<CIntentionCell>();
	/**
	 * All CICs in the IntentionView, indexed by the corresponding canvas id.
	 */
	private static Long2ReferenceArrayMap<CIntentionCell> cellsByCanvasId = new Long2ReferenceArrayMap<CIntentionCell>();

	public void populateState(IntentionalInterfaceState state)
	{
		for (CIntentionType type : activeIntentionTypes.values())
		{
			state.addCellPacket(type.getState());
		}

		for (CIntentionCell cell : cells.values())
		{
			cell.populateState(state);
		}
	}

	public void clearState()
	{
		activeIntentionTypes.clear();
		cells.clear();
		cellsByCanvasId.clear();
	}

	public Collection<CIntentionCell> getAllCells()
	{
		return cells.values();
	}

	public void addCell(CIntentionCell cell)
	{
		cells.put(cell.getId(), cell);
		cellsByCanvasId.put(cell.getCanvasId(), cell);
	}

	public CIntentionCell getCellById(long uuid)
	{
		return cells.get(uuid);
	}

	public CIntentionCell getCellByCanvasId(long canvas_uuid)
	{
		return cellsByCanvasId.get(canvas_uuid);
	}

	public void removeCellById(long uuid)
	{
		cells.remove(uuid);
	}

	/**
	 * Create a new intention type. If <code>colorIndex < 0</code>, assigs a randomly chosen color to the new intention
	 * type.
	 */
	public CIntentionType createIntentionType(long uuid, String name, int colorIndex)
	{
		if (colorIndex < 0)
		{
			colorIndex = chooseColorIndex();
		}

		CIntentionType type = new CIntentionType(uuid, name, colorIndex);
		activeIntentionTypes.put(uuid, type);
		return type;
	}

	/**
	 * Randomly choose a color which is not presently assigned to any intention type. Returns 0 when all colors are
	 * assigned to at least one intention type.
	 */
	private int chooseColorIndex()
	{
		boolean[] used = new boolean[CIntentionType.AVAILABLE_COLOR_COUNT];
		Arrays.fill(used, false);
		for (CIntentionType type : activeIntentionTypes.values())
		{
			used[type.getColorIndex()] = true;
		}
		for (int i = 0; i < used.length; i++)
		{
			if (!used[i])
			{
				return i;
			}
		}

		System.out.println("Warning: no unused CIntentionType colors to choose from!");
		return 0;
	}

	/**
	 * GEt the current set of intention types.
	 */
	public Collection<CIntentionType> getActiveIntentionTypes()
	{
		return activeIntentionTypes.values();
	}

	public void renameIntentionType(long typeId, String name)
	{
		activeIntentionTypes.get(typeId).setName(name);
	}

	public void setIntentionTypeColor(long typeId, int color)
	{
		activeIntentionTypes.get(typeId).setColorIndex(color);
	}

	public void removeIntentionType(long typeId)
	{
		activeIntentionTypes.remove(typeId);

		for (CIntentionCell cell : cells.values())
		{
			if (cell.hasIntentionType())
			{
				cell.clearIntentionType();
			}
		}
	}
}
