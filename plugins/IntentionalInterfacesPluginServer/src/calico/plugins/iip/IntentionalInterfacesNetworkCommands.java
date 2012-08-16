package calico.plugins.iip;

import calico.networking.netstuff.CalicoPacket;

/**
 * Network commands used by this plugin.
 * 
 * @author Byron Hawkins
 */
public class IntentionalInterfacesNetworkCommands
{
	// this block of static ids is required by a reflection component of the plugin system, which examines the class
	// designated for network commands for public static final int fields and takes them to be command ids.
	public static final int CIC_CREATE = Command.CIC_CREATE.id;
	public static final int CIC_MOVE = Command.CIC_MOVE.id;
	public static final int CIC_SET_TITLE = Command.CIC_SET_TITLE.id;
	public static final int CIC_TAG = Command.CIC_TAG.id;
	public static final int CIC_UNTAG = Command.CIC_UNTAG.id;
	public static final int CIC_DELETE = Command.CIC_DELETE.id;
	public static final int CIC_TOPOLOGY = Command.CIC_TOPOLOGY.id;
	public static final int CIC_CLUSTER_GRAPH = Command.CIC_CLUSTER_GRAPH.id;
	public static final int CIT_CREATE = Command.CIT_CREATE.id;
	public static final int CIT_RENAME = Command.CIT_RENAME.id;
	public static final int CIT_SET_COLOR = Command.CIT_SET_COLOR.id;
	public static final int CIT_DELETE = Command.CIT_DELETE.id;
	public static final int CLINK_CREATE = Command.CLINK_CREATE.id;
	public static final int CLINK_MOVE_ANCHOR = Command.CLINK_MOVE_ANCHOR.id;
	public static final int CLINK_LABEL = Command.CLINK_LABEL.id;
	public static final int CLINK_DELETE = Command.CLINK_DELETE.id;

	public enum Command
	{
		/**
		 * Notify the clients that a new <code>CIntentionCell</code> has been created. This command is also designated
		 * for sending to the server to request creation of a new CIC, though this behavior is not currently supported.
		 */
		CIC_CREATE,
		/**
		 * Set the pixel coordinates of a CIC.
		 */
		CIC_MOVE,
		/**
		 * Set the title of a CIC.
		 */
		CIC_SET_TITLE,
		/**
		 * Set the tag of a CIC, selected from among the <code>CIntentionType</code>s.
		 */
		CIC_TAG,
		/**
		 * Remove a tag from a CIC. This behavior is currently not used.
		 */
		CIC_UNTAG,
		/**
		 * Delete a CIC. This command is currently not supported.
		 */
		CIC_DELETE,
		/**
		 * Broadcast the cluster layout topology to all clients. This command is never received by the server.
		 */
		CIC_TOPOLOGY,
		/**
		 * Broadcast the cluster graph to all clients. This command is never received by the server.
		 */
		CIC_CLUSTER_GRAPH,
		/**
		 * Create a new <code>CIntentionType</code>. This command is currently only used for persistence.
		 */
		CIT_CREATE,
		/**
		 * Rename a <code>CIntentionType</code>. This command is currently not used.
		 */
		CIT_RENAME,
		/**
		 * Set the color of a <code>CIntentionType</code>. This command is currently not used.
		 */
		CIT_SET_COLOR,
		/**
		 * Delete a <code>CIntentionType</code>. This command is currently not used.
		 */
		CIT_DELETE,
		/**
		 * Create a new <code>CCanvasLink</code>.
		 */
		CLINK_CREATE,
		/**
		 * Move the pixel coordinates of a <code>CCanvasLinkAnchor</code>. This command is also designated for changing
		 * the canvases to which a link is attached, though this behavior is not currently supported.
		 */
		CLINK_MOVE_ANCHOR,
		/**
		 * Set the label of a <code>CCanvasLink</code>.
		 */
		CLINK_LABEL,
		/**
		 * Delete a <code>CCanvasLink</code>.
		 */
		CLINK_DELETE;

		public final int id;

		private Command()
		{
			this.id = ordinal() + OFFSET;
		}

		/**
		 * Print a warning to sysout if <code>p</code> is being processed as this kind of <code>Command</code> but
		 * actually has a different type id.
		 */
		public boolean verify(CalicoPacket p)
		{
			int type = p.getInt();
			boolean verified = forId(type) == this;
			if (!verified)
			{
				System.out.println("Warning: incorrect processing path for packet of type " + type);
			}
			return verified;
		}

		private static final int OFFSET = 2300;

		/**
		 * Get the command associated with <code>id</code>.
		 */
		public static Command forId(int id)
		{
			return Command.values()[id - OFFSET];
		}

		/**
		 * Return true if <code>id</code> is a command in the domain of this plugin.
		 */
		public static boolean isInDomain(int id)
		{
			return (id >= OFFSET) && (id < (OFFSET + 100));
		}
	}
}
