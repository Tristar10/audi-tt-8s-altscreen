package local.tt;

import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3D;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DImage;
import de.esolutions.hmi.widgets.audi.base.eal.IWrappedNode3DText;

/**
 * HMI-thread state of one layout slot: its own group node (keeps the draw order of the layout),
 * one picture per colour the slot has used, or one text node. A plain class.
 */
final class TeleSlot {
    static final int MAX_COLOURS = 6;

    IWrappedNode3D group;
    final int[] colours = new int[MAX_COLOURS];
    final IWrappedNode3DImage[] imgs = new IWrappedNode3DImage[MAX_COLOURS];
    int nImgs;
    IWrappedNode3DImage shownImg;
    IWrappedNode3DText txt;
    boolean shownVis;
    int shownColour = 1; // never a real colour with alpha 1 here
    String shownText;
    int sx = Integer.MIN_VALUE;
    int sy = Integer.MIN_VALUE;
    int sw = -1;
    int sh = -1;
    float shownRot = Float.NaN;
    /** Slot 0 (the full-screen background) with the itasha theme on: its picture (tele17). */
    IWrappedNode3DImage themeImg;
}
