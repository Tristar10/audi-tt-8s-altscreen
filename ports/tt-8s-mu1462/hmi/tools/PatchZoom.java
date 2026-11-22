import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.objectweb.asm.ClassReader;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.tree.ClassNode;
import org.objectweb.asm.tree.InsnList;
import org.objectweb.asm.tree.InsnNode;
import org.objectweb.asm.tree.JumpInsnNode;
import org.objectweb.asm.tree.LabelNode;
import org.objectweb.asm.tree.MethodInsnNode;
import org.objectweb.asm.tree.MethodNode;
import org.objectweb.asm.tree.VarInsnNode;

/**
 * Builds the TT zoom add-on classes from the car's own converted HMI.
 *
 * usage: PatchZoom stock.jar ZoomGate.class(javac output) outdir
 *
 * 0. AbstractGUI.increment/decrement(III)V (the TT main-map wheel path) get
 *        if (ZoomGate.gui(this, id, +-steps)) return;
 * 1. CombiBAPListener.setMapScale(I)V gets a prologue:
 *        if (ZoomGate.zoom(steps)) return;
 *    Everything else in the class is left as converted from the stock image.
 * 2. ZoomGate is rewritten to the stock class version without stack-map
 *    frames, so the unit's J9 loads it like the stock classes.
 */
public final class PatchZoom implements Opcodes {
    static final String TARGET = "de/audi/tghu/navi/app/cluster/CombiBAPListener";
    static final String GATE = "local/tt/ZoomGate";
    static final String GUI = "de/audi/tghu/navi/app/map/gui/AbstractGUI";

    static byte[] read(String jar, String cls) throws Exception {
        try (ZipFile zip = new ZipFile(jar)) {
            ZipEntry e = zip.getEntry(cls + ".class");
            if (e == null) throw new IllegalStateException("stock class missing: " + cls);
            try (InputStream in = zip.getInputStream(e)) { return in.readAllBytes(); }
        }
    }

    /** AbstractGUI.increment/decrement(III)V: if (ZoomGate.gui(this, id, +-steps)) return; */
    static void patchGui(String jar, String outdir, int version) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, GUI)).accept(cn, 0);
        if (cn.version != version) throw new IllegalStateException("AbstractGUI version " + cn.version);
        int inc = 0, dec = 0;
        for (MethodNode m : cn.methods) {
            boolean isInc = m.name.equals("increment"), isDec = m.name.equals("decrement");
            if ((!isInc && !isDec) || !m.desc.equals("(III)V")) continue;
            if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException(m.name + " is static");
            InsnList pre = new InsnList();
            LabelNode stockPath = new LabelNode();
            pre.add(new VarInsnNode(ALOAD, 0));
            pre.add(new VarInsnNode(ILOAD, 1));
            pre.add(new VarInsnNode(ILOAD, 2));
            if (isDec) pre.add(new InsnNode(INEG));
            pre.add(new MethodInsnNode(INVOKESTATIC, GATE, "gui", "(Ljava/lang/Object;II)Z", false));
            pre.add(new JumpInsnNode(IFEQ, stockPath));
            pre.add(new InsnNode(RETURN));
            pre.add(stockPath);
            m.instructions.insert(pre);
            if (isInc) inc++; else dec++;
        }
        if (inc != 1 || dec != 1) throw new IllegalStateException("expected one increment and one decrement, found " + inc + "/" + dec);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, GUI + ".class"), cw.toByteArray());
        System.out.println("patched " + GUI + ".increment/decrement (class version " + version + ")");
    }

    /** Inserts a logging call at the start of one method; the stock body always runs. */
    static void patchLog(String jar, String outdir, String cls, String method, String desc,
                         String gateMethod, String gateDesc, boolean passThis) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, cls)).accept(cn, 0);
        if (cn.version > V1_2) throw new IllegalStateException(cls + " version " + cn.version);
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals(method) || !m.desc.equals(desc)) continue;
            if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException(method + " is static");
            InsnList pre = new InsnList();
            if (passThis) pre.add(new VarInsnNode(ALOAD, 0));
            pre.add(new VarInsnNode(desc.startsWith("(I") ? ILOAD : ALOAD, 1));
            pre.add(new MethodInsnNode(INVOKESTATIC, GATE, gateMethod, gateDesc, false));
            m.instructions.insert(pre);
            n++;
        }
        if (n != 1) throw new IllegalStateException("expected one " + cls + "." + method + desc + ", found " + n);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, cls + ".class"), cw.toByteArray());
        System.out.println("patched " + cls + "." + method + " (logging, class version " + cn.version + ")");
    }

    /** DSICarTimeUnitsLanguageManager.updateSkin(II)V: ZoomGate.skinUpdate(skin, validity); then the stock body. */
    static void patchSkinUpdate(String jar, String outdir) throws Exception {
        String cls = "de/audi/app/settings/timeunitslang/dsi/DSICarTimeUnitsLanguageManager";
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, cls)).accept(cn, 0);
        if (cn.version > V1_2) throw new IllegalStateException(cls + " version " + cn.version);
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("updateSkin") || !m.desc.equals("(II)V")) continue;
            if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException("updateSkin is static");
            InsnList pre = new InsnList();
            pre.add(new VarInsnNode(ILOAD, 1));
            pre.add(new VarInsnNode(ILOAD, 2));
            pre.add(new MethodInsnNode(INVOKESTATIC, GATE, "skinUpdate", "(II)V", false));
            m.instructions.insert(pre);
            n++;
        }
        if (n != 1) throw new IllegalStateException("expected one updateSkin(II)V, found " + n);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, cls + ".class"), cw.toByteArray());
        System.out.println("patched " + cls + ".updateSkin (class version " + cn.version + ")");
    }

    /** static AnimationMIB2HighSport.setViewSize(eal, layout, small): ZoomGate.stage(small); then the stock body. */
    static void patchStage(String jar, String outdir) throws Exception {
        String cls = "de/esolutions/hmi/widgets/audi/evo/high/AnimationMIB2HighSport";
        String desc = "(Lde/esolutions/hmi/widgets/audi/base/eal/EALManager;Lde/esolutions/hmi/widgets/audi/base/Layout;Z)V";
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, cls)).accept(cn, 0);
        if (cn.version > V1_2) throw new IllegalStateException(cls + " version " + cn.version);
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("setViewSize") || !m.desc.equals(desc)) continue;
            if ((m.access & ACC_STATIC) == 0) throw new IllegalStateException("setViewSize is not static");
            InsnList pre = new InsnList();
            pre.add(new VarInsnNode(ALOAD, 0));
            pre.add(new MethodInsnNode(INVOKESTATIC, "local/tt/TtPanel", "eal", "(Ljava/lang/Object;)V", false));
            pre.add(new VarInsnNode(ILOAD, 2));
            pre.add(new MethodInsnNode(INVOKESTATIC, GATE, "stage", "(Z)V", false));
            m.instructions.insert(pre);
            n++;
        }
        if (n != 1) throw new IllegalStateException("expected one static setViewSize, found " + n);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, cls + ".class"), cw.toByteArray());
        System.out.println("patched " + cls + ".setViewSize (class version " + cn.version + ")");
    }

    /**
     * v63 settings page. AbstractScreenFactory (framework base of every app's screen factory):
     *   getScreen(II): every return value goes through ScreenHook.created(screen, this, id, terminal)
     *   getText(I):    s = ScreenHook.text(id); if (s != null) return s;   then the stock lookup
     */
    static void patchScreenFactory(String jar, String outdir) throws Exception {
        String cls = "de/audi/atip/hmi/view/AbstractScreenFactory";
        String screen = "de/audi/atip/hmi/view/Screen";
        String hook = "local/tt/ScreenHook";
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, cls)).accept(cn, 0);
        if (cn.version > V1_4) throw new IllegalStateException(cls + " version " + cn.version);
        int screens = 0, texts = 0;
        for (MethodNode m : cn.methods) {
            if (m.name.equals("getScreen") && m.desc.equals("(II)L" + screen + ";")) {
                if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException("getScreen is static");
                for (var insn : m.instructions.toArray()) {
                    if (insn.getOpcode() != ARETURN) continue;
                    InsnList post = new InsnList();
                    post.add(new VarInsnNode(ALOAD, 0));
                    post.add(new VarInsnNode(ILOAD, 1));
                    post.add(new VarInsnNode(ILOAD, 2));
                    post.add(new MethodInsnNode(INVOKESTATIC, hook, "created",
                        "(Ljava/lang/Object;Ljava/lang/Object;II)Ljava/lang/Object;", false));
                    post.add(new org.objectweb.asm.tree.TypeInsnNode(CHECKCAST, screen));
                    m.instructions.insertBefore(insn, post);
                    screens++;
                }
            } else if (m.name.equals("getText") && m.desc.equals("(I)Ljava/lang/String;")) {
                if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException("getText is static");
                InsnList pre = new InsnList();
                LabelNode stock = new LabelNode();
                pre.add(new VarInsnNode(ILOAD, 1));
                pre.add(new MethodInsnNode(INVOKESTATIC, hook, "text", "(I)Ljava/lang/String;", false));
                pre.add(new InsnNode(DUP));
                pre.add(new JumpInsnNode(IFNULL, stock));
                pre.add(new InsnNode(ARETURN));
                pre.add(stock);
                pre.add(new InsnNode(POP));
                m.instructions.insert(pre);
                texts++;
            }
        }
        if (screens != 1 || texts != 1) throw new IllegalStateException("expected one getScreen return and one getText(I), found " + screens + "/" + texts);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, cls + ".class"), cw.toByteArray());
        System.out.println("patched " + cls + ".getScreen and getText (settings page hook, class version " + cn.version + ")");
    }

    /** CombiBAPListener.setCombiService(service): RgdBus.combi(this, service); then the stock body. */
    static void patchCombiService(ClassNode cn) {
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("setCombiService")
                || !m.desc.equals("(Lde/audi/atip/interapp/combi/bap/navi/CombiBAPServiceNavi;)V")) continue;
            if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException("setCombiService is static");
            InsnList pre = new InsnList();
            pre.add(new VarInsnNode(ALOAD, 0));
            pre.add(new VarInsnNode(ALOAD, 1));
            pre.add(new MethodInsnNode(INVOKESTATIC, "local/tt/RgdBus", "combi", "(Ljava/lang/Object;Ljava/lang/Object;)V", false));
            m.instructions.insert(pre);
            n++;
        }
        if (n != 1) throw new IllegalStateException("expected one setCombiService, found " + n);
    }

    /**
     * Logging hooks at the start of instance methods: NavSpy.<spy>(args...); then the stock body.
     * Each spec is {method, descriptor, NavSpy method}; the NavSpy method takes the same arguments.
     */
    /**
     * After every DSIResponseContainer.isRgActive()Z call in the class:
     * TtRgi.rgActive(stockValue) - the stock flag OR an active CarPlay route.
     * Returns how many call sites were wrapped.
     */
    static int wrapRgActive(ClassNode cn) {
        int n = 0;
        for (MethodNode m : cn.methods) {
            for (var insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode mi = (MethodInsnNode) insn;
                if (mi.owner.equals("de/audi/tghu/navi/app/command/DSIResponseContainer")
                    && mi.name.equals("isRgActive") && mi.desc.equals("()Z")) {
                    m.instructions.insert(mi, new MethodInsnNode(INVOKESTATIC,
                        "de/audi/tghu/navi/app/cluster/TtRgi", "rgActive", "(Z)Z", false));
                    n++;
                }
            }
        }
        return n;
    }

    /**
     * ClusterService.updateRgActive(Z): the argument handed on to
     * ClusterKDKHandler.updateRgActive(Z) first goes through TtRgi.kdkRg(Z)Z.
     * The KDK handler shows the nav engine's guidance picture (displayable 20)
     * over the cockpit map; for a CarPlay-only route that picture is empty and
     * hides the CarPlay map, so TtRgi keeps it off unless stock guidance runs.
     */
    static int wrapKdk(ClassNode cn) {
        int n = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("updateRgActive") || !m.desc.equals("(Z)V")) continue;
            for (var insn : m.instructions.toArray()) {
                if (!(insn instanceof MethodInsnNode)) continue;
                MethodInsnNode mi = (MethodInsnNode) insn;
                if (mi.owner.equals("de/audi/tghu/navi/app/cluster/ClusterKDKHandler")
                    && mi.name.equals("updateRgActive") && mi.desc.equals("(Z)V")) {
                    m.instructions.insertBefore(mi, new MethodInsnNode(INVOKESTATIC,
                        "de/audi/tghu/navi/app/cluster/TtRgi", "kdkRg", "(Z)Z", false));
                    n++;
                }
            }
        }
        return n;
    }

    /** ClusterViewMode: only the isRgActive wrap. */
    static void patchViewMode(String jar, String outdir) throws Exception {
        String cls = "de/audi/tghu/navi/app/cluster/ClusterViewMode";
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, cls)).accept(cn, 0);
        if (cn.version > V1_2) throw new IllegalStateException(cls + " version " + cn.version);
        int n = wrapRgActive(cn);
        if (n != 3) throw new IllegalStateException("expected 3 isRgActive calls in ClusterViewMode, found " + n);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, cls + ".class"), cw.toByteArray());
        System.out.println("patched " + cls + ": " + n + " isRgActive calls wrapped (class version " + cn.version + ")");
    }

    static void patchSpy(String jar, String outdir, String cls, String[][] specs) throws Exception {
        ClassNode cn = new ClassNode();
        new ClassReader(read(jar, cls)).accept(cn, 0);
        if (cn.version > V1_2) throw new IllegalStateException(cls + " version " + cn.version);
        if (cls.equals("de/audi/tghu/navi/app/cluster/ClusterService")) {
            int w = wrapRgActive(cn);
            if (w != 1) throw new IllegalStateException("expected 1 isRgActive call in ClusterService, found " + w);
            System.out.println("wrapped " + w + " isRgActive call in ClusterService");
            int k = wrapKdk(cn);
            if (k != 1) throw new IllegalStateException("expected 1 ClusterKDKHandler.updateRgActive call, found " + k);
            System.out.println("filtered " + k + " ClusterKDKHandler.updateRgActive call in ClusterService");
        }
        if (cls.equals("de/audi/tghu/navi/app/cluster/KOMOService")) {
            // updateVisibility(visible, status): visible = TtRgi.komoVisible(visible) (inserted first,
            // so the logging hook added below still sees the nav engine's own value).
            int f = 0;
            for (MethodNode m : cn.methods) {
                if (!m.name.equals("updateVisibility") || !m.desc.equals("(ZI)V")) continue;
                InsnList pre = new InsnList();
                pre.add(new VarInsnNode(ILOAD, 1));
                pre.add(new MethodInsnNode(INVOKESTATIC, "de/audi/tghu/navi/app/cluster/TtRgi", "komoVisible", "(Z)Z", false));
                pre.add(new VarInsnNode(ISTORE, 1));
                m.instructions.insert(pre);
                f++;
            }
            if (f != 1) throw new IllegalStateException("expected one KOMOService.updateVisibility, found " + f);
            System.out.println("filtered KOMOService.updateVisibility through TtRgi.komoVisible");
        }
        for (String[] sp : specs) {
            int n = 0;
            for (MethodNode m : cn.methods) {
                if (!m.name.equals(sp[0]) || !m.desc.equals(sp[1])) continue;
                if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException(sp[0] + " is static");
                InsnList pre = new InsnList();
                org.objectweb.asm.Type[] argTypes = org.objectweb.asm.Type.getArgumentTypes(sp[1]);
                int slot = 1;
                for (org.objectweb.asm.Type t : argTypes) {
                    pre.add(new VarInsnNode(t.getOpcode(ILOAD), slot));
                    slot += t.getSize();
                }
                pre.add(new MethodInsnNode(INVOKESTATIC, "local/tt/NavSpy", sp[2],
                    org.objectweb.asm.Type.getMethodDescriptor(org.objectweb.asm.Type.VOID_TYPE, argTypes), false));
                m.instructions.insert(pre);
                n++;
            }
            if (n != 1) throw new IllegalStateException("expected one " + cls + "." + sp[0] + sp[1] + ", found " + n);
        }
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(outdir, cls + ".class"), cw.toByteArray());
        System.out.println("patched " + cls + " with " + specs.length + " logging hooks (class version " + cn.version + ")");
    }

    public static void main(String[] args) throws Exception {
        byte[] stock = read(args[0], TARGET);
        ClassNode cn = new ClassNode();
        new ClassReader(stock).accept(cn, 0);
        int version = cn.version;
        int patched = 0;
        for (MethodNode m : cn.methods) {
            if (!m.name.equals("setMapScale") || !m.desc.equals("(I)V")) continue;
            if ((m.access & ACC_STATIC) != 0) throw new IllegalStateException("setMapScale is static");
            InsnList pre = new InsnList();
            LabelNode stockPath = new LabelNode();
            pre.add(new VarInsnNode(ILOAD, 1));
            pre.add(new MethodInsnNode(INVOKESTATIC, GATE, "zoom", "(I)Z", false));
            pre.add(new JumpInsnNode(IFEQ, stockPath));
            pre.add(new InsnNode(RETURN));
            pre.add(stockPath);
            m.instructions.insert(pre);
            patched++;
        }
        if (patched != 1) throw new IllegalStateException("expected one setMapScale(I)V, found " + patched);
        patchCombiService(cn);
        if (version > V1_4) throw new IllegalStateException("unexpected class version " + version);
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
        cn.accept(cw);
        write(Path.of(args[2], TARGET + ".class"), cw.toByteArray());
        System.out.println("patched " + TARGET + ".setMapScale and setCombiService (class version " + version + ")");
        patchGui(args[0], args[2], version);
        patchLog(args[0], args[2], "de/audi/tghu/fwhmi/evo/DisplayManagerEvo", "setupKDKBackground",
                 "(IZ)V", "skin", "(I)V", false);
        patchSkinUpdate(args[0], args[2]);
        patchStage(args[0], args[2]);
        patchSpy(args[0], args[2], "de/audi/tghu/navi/app/cluster/ClusterService", new String[][] {
            { "updateRGIString", "([S)V", "rgi" },
            { "updateRgActive", "(Z)V", "rgActive" },
            { "updateTurnToStreet", "(Ljava/lang/String;Z)V", "turnTo" },
            { "updateCurrentStreet", "(Ljava/lang/String;)V", "street" },
            { "refreshViewMode", "(I)V", "viewMode" },
            { "updateManeuverDescriptor", "([Lorg/dsi/ifc/navigation/BapManeuverDescriptor;)V", "maneuvers" },
            { "updateManeuverDescriptor", "([Lorg/dsi/ifc/navigation/BapManeuverDescriptor;I)V", "maneuversN" },
            { "updateLaneGuidance", "([Lorg/dsi/ifc/navigation/NavLaneGuidanceData;Z)V", "lanes" },
            { "updateBapTurnToInfo", "([Lorg/dsi/ifc/navigation/BapTurnToInfo;)V", "turnToInfo" },
            { "updateRgInfoForNextDestination", "(Lorg/dsi/ifc/navigation/RgInfoForNextDestination;)V", "nextDest" },
            { "updateDistanceToNextManeuver", "(Lorg/dsi/ifc/navigation/DistanceToNextManeuver;)V", "dtm" },
            { "updateExitView", "(I)V", "exitView" },
            { "updateBapManeuverState", "(I)V", "maneuverState" },
            { "updateNextManeuver", "(Lorg/dsi/ifc/komoview/RouteInfoElement;)V", "nextManeuver" },
            { "updateRgDirectionToNextDestination", "(S)V", "direction" },
        });
        patchViewMode(args[0], args[2]);
        patchScreenFactory(args[0], args[2]);
        patchSpy(args[0], args[2], "de/audi/tghu/navi/app/cluster/KOMOService", new String[][] {
            { "setRouteInfo", "([Lorg/dsi/ifc/komoview/RouteInfoElement;)V", "komoInfo" },
            { "setRouteInfoElement", "(Lorg/dsi/ifc/komoview/RouteInfoElement;)V", "komoElem" },
            { "setTurnToStreet", "(Ljava/lang/String;Ljava/lang/String;)V", "komoTurnTo" },
            { "setRgSelect", "(I)V", "komoRgSelect" },
            { "updateKomoViewEnabled", "(ZI)V", "komoEnabled" },
            { "updateVisibility", "(ZI)V", "komoVisible" },
            { "updateCurrentKomoViewType", "(II)V", "komoType" },
            { "komoViewResult", "(I)V", "komoResult" },
            { "updateGfxState", "(II)V", "komoGfx" },
            { "setRgSelectResult", "(I)V", "komoRgSelResult" },
        });

        // Every class javac produced (gate root = two levels above ZoomGate.class) is ours:
        // local/tt/* plus the cluster-package helper TtRgi.
        Path gateRoot = Path.of(args[1]).getParent().getParent().getParent();
        int rewritten = 0;
        try (var files = Files.walk(gateRoot)) {
            for (Path p : (Iterable<Path>) files.sorted()::iterator) {
                if (!p.toString().endsWith(".class") || p.getFileName().toString().startsWith("._")) continue;
                ClassNode gate = new ClassNode();
                new ClassReader(Files.readAllBytes(p)).accept(gate, ClassReader.SKIP_FRAMES);
                if (!gate.name.startsWith("local/tt/") && !gate.name.equals("de/audi/tghu/navi/app/cluster/TtRgi")
                    && !gate.name.equals("de/audi/tghu/connectivity/hmi/evohighmmikombi/TtSettingsPage"))
                    throw new IllegalStateException("unexpected class " + gate.name);
                if (gate.signature != null) throw new IllegalStateException(gate.name + " uses generics");
                for (MethodNode m : gate.methods) {
                    if (m.signature != null || (m.access & (ACC_BRIDGE | ACC_VARARGS | ACC_SYNTHETIC)) != 0)
                        throw new IllegalStateException(gate.name + "." + m.name + " uses a newer language feature");
                    for (var insn : m.instructions) {
                        if (insn.getOpcode() == INVOKEDYNAMIC) throw new IllegalStateException(gate.name + " uses invokedynamic");
                        if (insn instanceof MethodInsnNode && ((MethodInsnNode) insn).owner.equals("java/lang/StringBuilder"))
                            throw new IllegalStateException(gate.name + "." + m.name + " uses StringBuilder");
                    }
                }
                gate.version = version;
                ClassWriter gw = new ClassWriter(ClassWriter.COMPUTE_MAXS);
                gate.accept(gw);
                write(Path.of(args[2], gate.name + ".class"), gw.toByteArray());
                System.out.println("rewrote " + gate.name + " to class version " + version);
                rewritten++;
            }
        }
        if (rewritten < 6) throw new IllegalStateException("expected ZoomGate, RgdBus, NavSpy, ManeuverMapper, RgiPlayer and TtRgi, rewrote " + rewritten);
    }

    static void write(Path p, byte[] b) throws Exception {
        Files.createDirectories(p.getParent());
        Files.write(p, b);
    }
}
