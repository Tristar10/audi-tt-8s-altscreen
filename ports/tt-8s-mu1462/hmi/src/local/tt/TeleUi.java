package local.tt;

/**
 * Haldex control page (tele17, wiki/Telemetry.md): presets, mode and a manual lock slider, driven
 * by a few actions (steering-wheel keys through TeleKeys, the kit's `tele ui ...`, the simulator's
 * keys). One cursor runs over the presets, then MODE, then LOCK. Press on a preset applies it; on
 * MODE or LOCK it starts editing (roll changes the value, press sends, back cancels). Every
 * command is sent once and confirmed by the live stream (channel 22 preset, 12 mode, 23 lock
 * setpoint), not by the reply alone; refusals from the board are shown in words.
 * Board contract: OpenHaldex v1.1.21 docs/PRESETS.md. Shared by the head unit and the simulator.
 * Java 1.4 library only.
 */
public final class TeleUi {
    public static final int OPEN = 1;
    public static final int UP = 2;
    public static final int DOWN = 3;
    public static final int PRESS = 4;
    public static final int BACK = 5;
    public static final int CLOSE = 6;

    private static final int MODE_COUNT = 11; // 0..10 can be chosen; 11 MANUAL comes from the slider
    private static final int MODE_MAP = 9;
    private static final int LOCK_STEP = 10;
    private static final long ANSWER_MS = 3000L;
    private static final long MSG_MS = 3000L;
    private static final long IDLE_CLOSE_MS = 20000L;

    private static final Object LOCK = new Object();
    private static final int[] QUEUE = new int[16];
    private static int qn;

    private boolean open;
    private int cursor;
    private boolean edit;
    private int editMode = MODE_MAP;
    private int editLock = 30;
    private int pendKind; // 0 none, 1 preset, 2 mode, 3 lock
    private int pendVal;
    private int pendSeq = -1;
    private long pendAt;
    private String msg;
    private int msgRgba;
    private long msgAt;
    private long lastInput;

    /** From any thread (keys, kit file, simulator). */
    public static void post(int action) {
        synchronized (LOCK) {
            if (qn < QUEUE.length) {
                QUEUE[qn++] = action;
            }
        }
    }

    /** Action name from the kit / key map ("open", "up", ...), 0 if unknown. */
    public static int action(String a) {
        if (a == null) {
            return 0;
        }
        a = a.trim();
        return a.equals("open") ? OPEN : a.equals("up") ? UP : a.equals("down") ? DOWN : a.equals("press") ? PRESS
            : a.equals("back") ? BACK : a.equals("close") ? CLOSE : 0;
    }

    public boolean isOpen() {
        return open;
    }

    /** Once per tick, after the layout's values are in and before it is built. */
    public void tick(TeleLayout t, long now) {
        int[] a;
        synchronized (LOCK) {
            a = new int[qn];
            System.arraycopy(QUEUE, 0, a, 0, qn);
            qn = 0;
        }
        for (int i = 0; i < a.length; i++) {
            handle(t, a[i], now);
        }
        confirm(t, now);
        if (open && now - lastInput > IDLE_CLOSE_MS && pendKind == 0) {
            open = false;
            edit = false;
        }
        int items = t.catN + 2;
        if (cursor >= items) {
            cursor = items - 1;
        }
        t.uiOpen = open;
        t.uiCursor = cursor;
        t.uiEdit = edit;
        t.uiMode = editMode;
        t.uiLock = editLock;
        boolean showMsg = msg != null && (pendKind != 0 || now - msgAt < MSG_MS);
        t.uiMsg = showMsg ? msg : null;
        t.uiMsgRgba = msgRgba;
    }

    private void handle(TeleLayout t, int act, long now) {
        lastInput = now;
        int items = t.catN + 2;
        if (act == OPEN) {
            open = !open;
            edit = false;
            if (open) {
                cursor = Math.max(0, activeIndex(t));
            }
            return;
        }
        if (act == CLOSE) {
            open = false;
            edit = false;
            return;
        }
        if (!open) {
            return;
        }
        int modeItem = t.catN;
        int lockItem = t.catN + 1;
        if (act == UP || act == DOWN) {
            int d = act == DOWN ? 1 : -1;
            if (edit && cursor == modeItem) {
                editMode = (editMode + d + MODE_COUNT) % MODE_COUNT;
            } else if (edit && cursor == lockItem) {
                editLock = Math.max(0, Math.min(100, editLock + d * LOCK_STEP));
            } else {
                cursor = Math.max(0, Math.min(items - 1, cursor + d));
            }
        } else if (act == BACK) {
            if (edit) {
                edit = false;
            } else {
                open = false;
            }
        } else if (act == PRESS) {
            if (cursor < t.catN) {
                send(t, TeleLink.CMD_APPLY, t.catId[cursor], 1, now, new StringBuffer("PRESET ")
                    .append(t.catName[cursor] == null ? "?" : t.catName[cursor].toUpperCase()).toString());
            } else if (!edit) {
                edit = true;
                int m = t.has[TeleLayout.CH_MODE] ? (t.val[TeleLayout.CH_MODE] + 500) / 1000 : MODE_MAP;
                int l = t.has[TeleLayout.CH_LOCK_SET] ? (t.val[TeleLayout.CH_LOCK_SET] + 500) / 1000 : 255;
                editMode = m >= 0 && m < MODE_COUNT ? m : MODE_MAP;
                editLock = l >= 0 && l <= 100 ? l - l % LOCK_STEP : editLock;
            } else {
                edit = false;
                if (cursor == modeItem) {
                    send(t, TeleLink.CMD_MODE, editMode, 2, now,
                        new StringBuffer("MODE ").append(TeleLayout.modeName(editMode)).toString());
                } else {
                    send(t, TeleLink.CMD_LOCK, editLock, 3, now,
                        new StringBuffer("LOCK ").append(editLock).append('%').toString());
                }
            }
        }
    }

    private void send(TeleLayout t, int cmd, int arg, int kind, long now, String what) {
        String r = TeleLink.command(cmd, arg);
        RgdBus.note(new StringBuffer("tele ui: ").append(what).append(" -> ").append(r).toString());
        if (r.indexOf("no link") >= 0) {
            say("NO LINK TO OPENHALDEX", TeleLayout.AMBER, now);
            return;
        }
        pendKind = kind;
        pendVal = arg;
        pendSeq = TeleLink.lastSeq();
        pendAt = now;
        say(new StringBuffer(what).append(" ...").toString(), TeleLayout.GREY, now);
    }

    private void confirm(TeleLayout t, long now) {
        if (pendKind == 0) {
            return;
        }
        boolean ok;
        if (pendKind == 1) {
            ok = chan(t, TeleLayout.CH_PRESET) == pendVal;
        } else if (pendKind == 2) {
            ok = chan(t, TeleLayout.CH_MODE) == pendVal;
        } else {
            ok = chan(t, TeleLayout.CH_MODE) == 11 && chan(t, TeleLayout.CH_LOCK_SET) == pendVal;
        }
        if (ok) {
            String what = msg != null && msg.endsWith(" ...") ? msg.substring(0, msg.length() - 4) : "DONE";
            say(new StringBuffer(what).append(" ON").toString(), TeleLayout.ACTIVE, now);
            pendKind = 0;
            return;
        }
        int res = TeleLink.resultFor(pendSeq);
        if (res > 0) {
            say(refusal(res), TeleLayout.AMBER, now);
            pendKind = 0;
        } else if (now - pendAt > ANSWER_MS) {
            say("NO ANSWER FROM OPENHALDEX", TeleLayout.AMBER, now);
            pendKind = 0;
        }
    }

    private static int chan(TeleLayout t, int ch) {
        return t.link && t.has[ch] ? (t.val[ch] + 500) / 1000 : -1;
    }

    private static int activeIndex(TeleLayout t) {
        int act = chan(t, TeleLayout.CH_PRESET);
        for (int i = 0; i < t.catN; i++) {
            if (t.catId[i] == act) {
                return i;
            }
        }
        return t.catN > 0 ? 0 : t.catN;
    }

    private static String refusal(int r) {
        switch (r) {
            case 1:
                return "PRESET LIST CHANGED, TRY AGAIN";
            case 2:
                return "UNKNOWN PRESET";
            case 3:
                return "REFUSED: CALIBRATING";
            case 4:
                return "REFUSED: CAN VIEW ACTIVE";
            case 5:
                return "PRESET MAP MISSING";
            case 6:
                return "VALUE OUT OF RANGE";
            case 255:
                return "NOT SUPPORTED: UPDATE OPENHALDEX";
            default:
                return new StringBuffer("REFUSED (").append(r).append(')').toString();
        }
    }

    private void say(String s, int rgba, long now) {
        msg = s;
        msgRgba = rgba;
        msgAt = now;
    }
}
