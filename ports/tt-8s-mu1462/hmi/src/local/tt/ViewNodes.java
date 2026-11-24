package local.tt;

import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3D;

/** One view's node group and slots, kept while the other view shows (tele11). A plain class. */
final class ViewNodes {
    IWrappedNode3D group;
    TeleSlot[] slots;
}
