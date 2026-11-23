package local.tt;

import java.io.File;
import java.io.FileInputStream;

import de.audi.tghu.navi.app.cluster.TtRgi;

/**
 * Parked test playback for the TT cockpit turn display (tt-zoom v14, v15: state command).
 *
 * While /tmp/tt-rgi-play.txt exists, its lines are played in order and the
 * iPhone feed is paused. One command per line:
 *   # comment
 *   wait 3000            pause in milliseconds
 *   loop                 start again from the first line
 *   say any text         just written to the log, to mark a step
 *   active 1 | icon 13,64,-1,0,-64 | state 3 | turn 250 [bar] | dest 5300
 *   eta 600 | road Main St | next Elm St | exitview 5 | clear
 * (see TtRgi.raw). The file is re-read when it changes, so a new script can be
 * copied over a running one. Deleting it ends playback: the display is cleared
 * and the iPhone feed takes over again. Every step goes to /tmp/tt-rgd.log.
 *
 * Java 1.4 class library: StringBuffer only.
 */
public final class RgiPlayer implements Runnable {
    private static final File SCRIPT = new File("/tmp/tt-rgi-play.txt");
    private static volatile boolean playing;
    private static boolean started;

    private RgiPlayer() {
    }

    /** True while a script is playing; RgdBus then leaves the display alone. */
    public static boolean playing() {
        return playing;
    }

    public static synchronized void start() {
        if (started) {
            return;
        }
        started = true;
        Thread t = new Thread(new RgiPlayer(), "tt-rgi-player");
        t.setDaemon(true);
        t.start();
    }

    public void run() {
        while (true) {
            try {
                if (!SCRIPT.exists()) {
                    sleep(1000);
                    continue;
                }
                playing = true;
                RgdBus.note("play: script found");
                play();
            } catch (Throwable t) {
                RgdBus.note(new StringBuffer("play: stopped ").append(String.valueOf(t)).toString());
                sleep(2000);
            }
            if (!SCRIPT.exists() && playing) {
                RgdBus.replayEnd();
                RgdBus.note(TtRgi.raw("clear", ""));
                RgdBus.note(TtPanel.cmd("off"));
                playing = false;
                RgdBus.note("play: ended, iPhone feed resumes");
            }
        }
    }

    /** Plays the script until the file is deleted; restarts when it changes. */
    private static void play() {
        String[] lines = read();
        long stamp = SCRIPT.lastModified();
        int i = 0;
        while (SCRIPT.exists()) {
            if (SCRIPT.lastModified() != stamp) {
                lines = read();
                stamp = SCRIPT.lastModified();
                i = 0;
                RgdBus.note("play: script changed, restarting");
            }
            if (i >= lines.length) {
                // Finished: hold the last state until the file is deleted or replaced.
                sleep(500);
                continue;
            }
            String line = lines[i++].trim();
            if (line.length() == 0 || line.charAt(0) == '#') {
                continue;
            }
            int sp = line.indexOf(' ');
            String cmd = sp < 0 ? line : line.substring(0, sp);
            String arg = sp < 0 ? "" : line.substring(sp + 1).trim();
            if (cmd.equals("wait")) {
                long ms = 1000;
                try {
                    ms = Long.parseLong(arg);
                } catch (Throwable t) {
                    // keep 1000
                }
                long end = System.currentTimeMillis() + ms;
                // v58: to the millisecond (replayed messages come 20-40 ms apart)
                long left;
                while ((left = end - System.currentTimeMillis()) > 0L && SCRIPT.exists()) {
                    sleep(left < 100L ? (int) left : 100);
                }
            } else if (cmd.equals("loop")) {
                i = 0;
                sleep(200);
            } else if (cmd.equals("say")) {
                RgdBus.note(new StringBuffer("play: say ").append(arg).toString());
            } else if (cmd.equals("panel")) {
                RgdBus.note(TtPanel.cmd(arg));
            } else if (cmd.equals("tele")) {
                RgdBus.note(TeleView.cmd(arg));
            } else if (cmd.equals("rgd")) {
                // v58: a recorded iPhone update through the live path (silent; RgdBus logs its own)
                RgdBus.replay(arg);
            } else if (cmd.equals("rgdclear")) {
                RgdBus.note(RgdBus.replayClear());
            } else {
                RgdBus.note(TtRgi.raw(cmd, arg));
            }
        }
    }

    private static String[] read() {
        try {
            FileInputStream in = new FileInputStream(SCRIPT.getPath());
            byte[] buf;
            try {
                int len = (int) Math.min(SCRIPT.length(), 1048576L);
                buf = new byte[len];
                int off = 0;
                while (off < len) {
                    int n = in.read(buf, off, len - off);
                    if (n < 0) {
                        break;
                    }
                    off += n;
                }
            } finally {
                in.close();
            }
            String text = new String(buf, "UTF-8");
            int count = 1;
            for (int k = 0; k < text.length(); k++) {
                if (text.charAt(k) == '\n') {
                    count++;
                }
            }
            String[] out = new String[count];
            int n = 0;
            int start = 0;
            for (int k = 0; k <= text.length(); k++) {
                if (k == text.length() || text.charAt(k) == '\n') {
                    String s = text.substring(start, k);
                    if (s.endsWith("\r")) {
                        s = s.substring(0, s.length() - 1);
                    }
                    out[n++] = s;
                    start = k + 1;
                }
            }
            return out;
        } catch (Throwable t) {
            RgdBus.note(new StringBuffer("play: cannot read script ").append(String.valueOf(t)).toString());
            return new String[0];
        }
    }

    private static void sleep(long ms) {
        try {
            Thread.sleep(ms);
        } catch (Throwable ignored) {
        }
    }
}
