#!/bin/sh
# TT CarPlay toolkit (Audi TT MU1462). One command for everything.
# After setup, from the SSH login folder:   sh tt.sh help
PATH=/proc/boot:/bin:/usr/bin:/sbin:/usr/sbin:/mnt/app/armle/bin:/mnt/app/armle/usr/bin
export PATH
K=/mnt/app/root/tt
JARS=/mnt/app/eso/hmi/lsd/jars
JAR=$JARS/tt-zoom.jar
V12=5026c7c44e180352c78e1f21627331735ea108a7c5445ed0eb367a78e6a45a21
V14=ae246f4c4b9e4bc0e1235a74a119be4481ef3534f9c371833092fafa9e5df5e1
V15=f13fdd3ab74f3f85fe70900b51da0186eab0b0ef837314f7e7d4be9e0f202d08
V16=cc4110d51cf639a6502f6dfb5bcaa676bb101a553c365b123b4bd51ad78242b4
V17=651454c8695f514b0627c7f0818422820ff44245fd3dd4c1483487fa58fd1ec9
V18=a403e83dc210a4ae580c547bf63c0bf235934483b1e4096efdd5ade427e3003f
V19=a549814352b72762f1639c0343eeab829f954aa2ff92e078d6fea40fd30fb4e1
V20=146a39998585e62b59eff8e714c3e3e92271a15fb72530cd3aafddeb9000c44b
V21=43a59e05a7b5f8821428d2410a1e96ee142bddfa063fcc02af981d827da47e09
V22=1619db75cd413a614d28d17a646524cf3e51ea1492f971a29eb71452e739f763
V23=74b71d9013d088ca8a38f6b035a75da15a1c00b519a53713299aee967b7056e7
V24=a0f1c832d7ddb6e776a3d6985cc857ee5093878235cae778da675deb6c757917
V25=5ea2b40ec7f48cb62c0f0ce3f0b2dbb28ed74adec2264dddc5ae8afe3b981573
V26=42bb2f9cc16ef62c904fb9542ec0dedb86361062ea7388bcda0d16689bdc2f38
V27=f980ba096d9805ebd9d8245974a99525e0d1f48cd3f68534c892bff3cf356adf
V28=76c5cfb667ce9160724ef8cfa0732630ee6f00acdfe35069c07337ef7282d8ad
V29=f2a3405ec73b3e2325bbfb950c2bf7321ac72f0668a288cf02c3db8eb8dbbf5a
V30=8d495fb124f657554084a05ba779be75a2abef5057d50638e6e4869a33f3a323
V31=a837524259224b5a62c41f2d23ccb9173ca6871e27d173b70d2397aa0bea2b88
V32=0128845e24b72cf59eb8f9ff5225422b42591926cfd44b9407620d5bfbc1a830
V33=d60fdb3d06402a71df22974c56e758becac30d24c65ad0ef08cfa0e2c279bcc1
V34=5b0e22986b3a4aa923784b49e27eb35a0b471e33e4a7f619941a848e82bd498f
V35=3d07121dae29006e3825c3b61520a5310212bc02dfab5245b6d0a00e614f9fe2
V36=03994ca30aeb49c99c10297ae991ce905477db76fc50007c7f300d8cbb2caab4
V37=9b20688cbc8d74991c7075746de6c30fe8686e0247bba62387aa7e1d9b5c66c4
V38=c3ad31212c5c364030be092dff941e7ffd39f02003cd7857d858f9684683822c
V39=0a7636fc436d53426f2684d08116172c0410651b9416914f69009ef611ef7d9f
V40=799e2cf291bfb05a728875c6c227dc5b9e93fd317796bcc1d20527dce7ac8c14
V41=0fdfdb631f0ad028af51d5e7bf78bab03d908877d344e0de03ba41c8a23864b4
V42=679ccf4a1c1951e909d1b7f7897f5b257dc89bd91b58ba8339075ebfdd7daa64
V43=b9f3e25770a2459eb6e2856d985fa9ddc763d3130345196d76d5e5382a1b5a38
V44=0bb8db47205430a9054ec4c9418dc71ce102dde92bc5c656013b30d9c0b7cdd8
V45=95ea381137bcfe69f2264499827a808ce7dc2dcc08b4e4dacf75acb5a5f4d8cb
V46=482620c0a5275b5d0614a73a59779c621151e67449efa4c77955cb0a28ba779e
V47=730bcef272b1eddd93bcaa530581135c4c6032cac9ea749364be854af1120d7c
V48=8b82caad8bedd32b6d55759404e5dc06abb04063ff4fbb6024047dca835ee063
V49=1731457db870d747bef9b7eab0ba10dc06f488412f8440e0b0b49742e4dbac3f
V50=ac5f1958763d70f4890f83ad0659807f6b18b7a67992acacaa36d4b6738b141e
V51=b01d20ed4f607aa19e8a12f5de58d670c28a7eb2320e05423cecf6c16653f2b4
V52=f634de2ab5857215fb0b0d691b1787cf0cc609243420b13de0facc1809606125
V53=bfcebae27e6c07d12f8b5942b77693d3616ee1d0afbfc96000870e8d0d73ee82
V54=52bbefb3fa760eb4fb0efa22b543223f0ea558658e5044c6a2443273b44a5caf
V55=5e756cb9a2c11070ad0a69cb198fcf4c901c7caca0bee4f82b9107e50b5d81fa
V56=909ab2a72d066c7a1a255699d2b84387b5dfb5d9ba9dbcfde01cebaf7cb8d2e3
# v57: v56 + panel state reports in tt-rgd.log, timestamps in tt-zoom.log (diagnostics only)
V57=caa687ef0ad2e5e1fc8905848e09a2bac9ab1b6e6c6b64d276ae2584851b9677
# v58: v57 + replay of recorded iPhone messages through the live path (sh tt.sh play reroute)
V58=d8e43af3f37e50251df4aa4290f4a82614ad162f917db584e8988e39a9944ed8
# v59: v58 + automatic route recorder (SD card tt-routes, RAM fallback /tmp/tt-routes)
V59=8611e08cbc7aa04759950047f1ac2c820505a0bb114d23d56f28fff511b9d3c0
# v60: v59 + permanent recorder off switch (/mnt/app/root/tt-rec-off, sh tt.sh routerec never)
V60=ad9981b5e02acf0cdd2d4198974956e71614ac1f8a0b4d4e7c081bb2a44e4b1d
# v61: v60 + banner arrival time fixed (it moved later by the time driven)
V61=bd4cb99d71019f3503d853f389572a44ed319844ee10d5a38987f3647b9bd6f7
# v62: v61 + banner distances in the phone's own words ("0.1 mi", not "550 ft")
V62=4e1446f14ba9a2fe751d76f07ebb1bf92806d12ba3510f9b6616de43623397e2
# v63: v62 + settings prototype: "iPhone turn card" drop-down in map options and MENU > Settings > MMI settings
V63=e43481032f9ef653c8e9236f8dbd1cca2a5d42e6efb19a42ac0961039676ad2b
SUP=/mnt/app/root/mu1438-cluster/cluster_supervisor.sh
SUP_V2=9175a6deaff6acf2c4f35b61b245476ba8827e7f4b74977b48cdafc2c7c6f57c
SUP_V3=4c37a375954a387e4938fbabcad868bb2fcc4207a97bcbe599421974e75f06bd
SUP_V4=d520d1971e843bdbdc2898a7e64b09b2fbb3d6b7495a6374a420769446bbb8ed
SUP_V5=5a746386705da855b4d88c33042122d54e44ebbd8516165325bf3839e0627282
SUP_V6=de728b175607fe702393297b7e6a73be5efea32ea29d86b23398f6f1938557d4
SUP_V7=e8dbabcaf69b08fb2532825ee5506c03e6603398f1a5293baea621136035ae26
SUP_V8=e2259c3d24762ae2d1e98062bc8fff558dfaedf86f47df2b4c7f0512c09d86a2
RT=/mnt/app/root/mu1438-cluster/cluster_runtime
RT_STAGE1=caf2fa48c341fd7bd1a860a1660c5eaad86b906c707a0abe3ff769a368855357
RT_SPORT=24bf08142cc43a18330550ca448f4635e10d5ab3ed38475e80d450a469991a7f
RT_TT=55bc66f1fe3dc1578d2408b932d4806eaab13c415636f42256c566512ccdec56
RT_TT2=400d50c84db5824e4a9cde5816c9c00139a0d35bdcff0d17c246edf66b9a19f7
RT_TT3=fec73a33d0b0d947afd3a1202aa1e5dc53891737305fb95d9cc9292b433efbbd
RT_TT4=28d73321002eb48c96f80bb85b320f944076d14f3be2b7e5e180ac251d39a8ab
RT_TT5=f2b3a9c8dd3050a40f60df021c4cab37a114196a517bf24916206b8f26742e84
RT_TT6=54b1a576c09a3f62765c78f940002e64cfc9cdffcdfa149e261dcbca1b649471
SUP_V9=04787ee28e63d315919b7467263c4cdea55862aa3dac521381a7afbfab4d3c1d
HOOK=/mnt/app/root/lib-target/libaltscreen111.so
# CarPlay hook builds: shipped, h320 safe-area preset (what the TT runs), and their fix1 versions
HOOK_STOCK=9a8c01ec8323d5de7695e212ff6168b0bb7df99d8554c9971f5449c5a21d6256
HOOK_H320=36aedadb8e0680ad03a9a511f3844e629ca3b2ffaaf40c9745298f730fc0b3f8
FIX_STOCK=9e4b13c12895c8bddc36586661ecd82bde71666e15a847b617993d7fcb8f0dee
FIX_H320=64f700f5fffb8f91a0e5ee0fedfa9e132eb2c6fb65da3c16a063e832421792e8
# built from source (scripts/wsl-native-build.sh tt): h320 + close-on-exec + stock delegates
HOOK_TT1=028b4a73a2e6b8e6eba5e4df4a058848c7059ebdf9433815d54bb4c86f908bf3
# TT build 2: build 1 + test overrides /tmp/tt-hook-mm and /tmp/tt-hook-safe (sh tt.sh look)
HOOK_TT2=347a911454a48cc494851178d4298c29b71d8854fd3087ce6cb403e51c7885c5
T=$K/altscreen-sha256

say(){ echo "$*"; }
dm(){ LD_LIBRARY_PATH=/eso/lib:/mnt/app/root/lib-target:/mnt/app/usr/lib:/mnt/app/armle/lib:/mnt/app/armle/lib/dll:/mnt/app/armle/usr/lib IPL_CONFIG_DIR=/etc/eso/production /eso/bin/apps/dmdt "$@"; }
# Dump the display manager state into /tmp/tt-dmdt.txt with a label.
slog(){ logger -p user.info "tt: $*" 2>/dev/null || slogger -c 19 "tt: $*" 2>/dev/null; }
mark(){ echo "$*" >> /tmp/tt-mark; for f in /tmp/tt-rgd.log /tmp/altscreen111.log /tmp/mu1438-cluster-runtime.log /tmp/mu1462-supervisor.log; do echo "===== MARK $* =====" >> $f; done; slog "MARK $*"
        { echo "===== MARK $*"; sloginfo 2>/dev/null | tail -300; } >> /tmp/tt-slog-marks.txt; say "Marked: $*"; }
# v59 route recordings: SD card(s) first, then RAM
ROUTEDIRS="/fs/sda0/tt-routes /fs/sdb0/tt-routes /tmp/tt-routes"
routefile(){ if test -n "$1"; then n=$(echo "$1" | awk '{printf "%04d", $1}'); for d in $ROUTEDIRS; do test -f "$d/route-$n.txt" && { echo "$d/route-$n.txt"; return; }; done; else l=$(for d in $ROUTEDIRS; do ls $d/route-*.txt 2>/dev/null; done); test -n "$l" && ls -t $l | head -1; fi; }
dmdump(){ { echo "===== $1"; echo '--- gs'; dm gs; echo '--- gc'; dm gc; echo '--- gd'; dm gd; } >> /tmp/tt-dmdt.txt 2>&1; }
# Displayables of one context, from the newest gc dump.
ctxlist(){ dm gc 2>/dev/null | awk -v c="$1" '$1==c && $2=="|" {f=1; next} f && /^[ \t]*-+/ {next} f && NF==0 {exit} f && $1 ~ /^-?[0-9]+$/ {printf "%s ", $1}'; }
rw(){ mount -uw /mnt/app || { say 'Cannot make /mnt/app writable.'; exit 1; }; }
ro(){ sync; mount -ur /mnt/app 2>/dev/null; }
which_jar(){
    h=$("$T" "$JAR" 2>/dev/null)
    case "$h" in
        $V12) echo v12;; $V14) echo v14;; $V15) echo v15;; $V16) echo v16;; $V17) echo v17;; $V18) echo v18;; $V19) echo v19;; $V20) echo v20;; $V21) echo v21;; $V22) echo v22;; $V23) echo v23;; $V24) echo v24;; $V25) echo v25;; $V26) echo v26;; $V27) echo v27;; $V28) echo v28;; $V29) echo v29;; $V30) echo v30;; $V31) echo v31;; $V32) echo v32;; $V33) echo v33;; $V34) echo v34;; $V35) echo v35;; $V36) echo v36;; $V37) echo v37;; $V38) echo v38;; $V39) echo v39;; $V40) echo v40;; $V41) echo v41;; $V42) echo v42;; $V43) echo v43;; $V44) echo v44;; $V45) echo v45;; $V46) echo v46;; $V47) echo v47;; $V48) echo v48;; $V49) echo v49;; $V50) echo v50;; $V51) echo v51;; $V52) echo v52;; $V53) echo v53;; $V54) echo v54;; $V55) echo v55;; $V56) echo v56;; $V57) echo v57;; $V58) echo v58;; $V59) echo v59;; $V60) echo v60;; $V61) echo v61;; $V62) echo v62;; $V63) echo v63;; "") echo none;; *) echo "unknown ($h)";;
    esac
}

case "${1:-help}" in
setup)
    # Run from /tmp after copying the kit there. Safe to run again for a kit update.
    test -f /tmp/tt-zoom-v63.jar || { say 'Copy the kit files to /tmp first.'; exit 1; }
    rw
    mkdir -p "$K/logs"
    for f in altscreen-sha256 tt-zoom-v63.jar stock-replay.txt catalogue.txt komo-test.txt panel-test.txt kdk-test.txt fit-test.txt win-test.txt new-test.txt map-test.txt place-test.txt; do
        cp "/tmp/$f" "$K/$f" || { say "missing /tmp/$f"; ro; exit 1; }
    done
    for f in tt-zoom-v12.jar tt-zoom-v14.jar tt-zoom-v15.jar tt-zoom-v16.jar tt-zoom-v17.jar tt-zoom-v18.jar tt-zoom-v19.jar tt-zoom-v20.jar tt-zoom-v21.jar tt-zoom-v22.jar tt-zoom-v23.jar tt-zoom-v24.jar tt-zoom-v25.jar tt-zoom-v26.jar tt-zoom-v27.jar tt-zoom-v28.jar tt-zoom-v29.jar tt-zoom-v30.jar tt-zoom-v31.jar tt-zoom-v32.jar tt-zoom-v33.jar tt-zoom-v34.jar tt-zoom-v35.jar tt-zoom-v36.jar tt-zoom-v37.jar tt-zoom-v42.jar tt-zoom-v43.jar tt-zoom-v44.jar tt-zoom-v50.jar tt-zoom-v51.jar tt-zoom-v52.jar tt-zoom-v53.jar tt-zoom-v54.jar tt-zoom-v55.jar tt-zoom-v56.jar tt-zoom-v57.jar tt-zoom-v58.jar tt-zoom-v59.jar tt-zoom-v60.jar tt-zoom-v61.jar tt-zoom-v62.jar reroute-replay.txt libaltscreen111-tt1.so libaltscreen111-tt2.so cluster_supervisor-v6.sh cluster_supervisor-v7.sh cluster_supervisor-v8.sh cluster_runtime-tt cluster_supervisor-v9.sh cluster_runtime-tt2 cluster_runtime-tt3 cluster_runtime-tt4 cluster_runtime-tt5 cluster_runtime-tt6; do
        test -f "/tmp/$f" && cp "/tmp/$f" "$K/$f"
    done
    # Optional panel background picture (made with scripts/mkbg.py; kept on the car, not in git).
    test -f /tmp/panel-bg.a8 && cp /tmp/panel-bg.a8 "$K/panel-bg.a8" && say 'Panel background installed.'
    cp /tmp/tt.sh /mnt/app/root/tt.sh
    chmod 755 "$T" /mnt/app/root/tt.sh
    ok=1
    "$T" $V63 "$K/tt-zoom-v63.jar" || ok=0
    ro
    if test $ok = 1; then say 'Kit installed. From now on just type:  sh tt.sh help'
    else say 'A kit file is damaged - copy the kit again and rerun setup.'; exit 1; fi
    ;;
use)
    v=${2:-}
    case "$v" in v12) h=$V12;; v14) h=$V14;; v15) h=$V15;; v16) h=$V16;; v17) h=$V17;; v18) h=$V18;; v19) h=$V19;; v20) h=$V20;; v21) h=$V21;; v22) h=$V22;; v23) h=$V23;; v24) h=$V24;; v25) h=$V25;; v26) h=$V26;; v27) h=$V27;; v28) h=$V28;; v29) h=$V29;; v30) h=$V30;; v31) h=$V31;; v32) h=$V32;; v33) h=$V33;; v34) h=$V34;; v35) h=$V35;; v36) h=$V36;; v37) h=$V37;; v38) h=$V38;; v39) h=$V39;; v40) h=$V40;; v41) h=$V41;; v42) h=$V42;; v43) h=$V43;; v44) h=$V44;; v45) h=$V45;; v46) h=$V46;; v47) h=$V47;; v48) h=$V48;; v49) h=$V49;; v50) h=$V50;; v51) h=$V51;; v52) h=$V52;; v53) h=$V53;; v54) h=$V54;; v55) h=$V55;; v56) h=$V56;; v57) h=$V57;; v58) h=$V58;; v59) h=$V59;; v60) h=$V60;; v61) h=$V61;; v62) h=$V62;; v63) h=$V63;; *) say 'Usage: sh tt.sh use v63   (or v62 ... v12)'; exit 2;; esac
    test -f "$K/tt-zoom-$v.jar" || { say "$v is not in the kit on the car."; exit 1; }
    "$T" $h "$K/tt-zoom-$v.jar" || { say "Stored $v jar is damaged."; exit 1; }
    rw
    cp "$K/tt-zoom-$v.jar" "$JAR.new" && chmod 755 "$JAR.new" && mv "$JAR.new" "$JAR"
    "$T" $h "$JAR" && r=0 || r=1
    ro
    test $r = 0 && say "Installed $v. Now restart the MMI." || say 'Copy failed - nothing changed.'
    ;;
remove)
    rw; rm -f "$JAR"; ro; say 'tt-zoom.jar removed. Restart the MMI for stock behaviour.'
    ;;
play)
    case "${2:-stock}" in
        stock) cp "$K/stock-replay.txt" /tmp/tt-rgi-play.txt && say 'Stock-nav replay playing (about 2 min, repeats). Stop: sh tt.sh stop';;
        win) cp "$K/win-test.txt" /tmp/tt-rgi-play.txt && say 'Window test playing (about 65 s, repeats): stay in the TACH-CENTRED view, one photo per step (W1, W2, W3). Stop: sh tt.sh stop';;
        map) cp "$K/map-test.txt" /tmp/tt-rgi-play.txt && say 'Map test playing (about 80 s, repeats): CarPlay connected, follow the steps M1..M4, one photo each. Stop: sh tt.sh stop';;
        new) cp "$K/new-test.txt" /tmp/tt-rgi-play.txt && say 'New pictures playing (about 75 s, repeats): one photo per step N1..N11, any view. Stop: sh tt.sh stop';;
        fit) cp "$K/fit-test.txt" /tmp/tt-rgi-play.txt && say 'Panel test playing (about 65 s, repeats): switch views and tabs while it runs. Stop: sh tt.sh stop';;
        kdk) cp "$K/kdk-test.txt" /tmp/tt-rgi-play.txt && say 'Banner-spot test 2 playing (about 110 s, repeats). Stop: sh tt.sh stop';;
        panel) cp "$K/panel-test.txt" /tmp/tt-rgi-play.txt && say 'Turn panel demo playing (about 2 min, repeats). Stop: sh tt.sh stop';;
        komo) cp "$K/komo-test.txt" /tmp/tt-rgi-play.txt && say 'KOMO test playing (about 100 s, repeats). Stop: sh tt.sh stop';;
        reroute) test -f "$K/reroute-replay.txt" || { say 'reroute-replay.txt is not in the kit.'; exit 1; }
                 cp "$K/reroute-replay.txt" /tmp/tt-rgi-play.txt && say 'Drive replay playing: the I-10 ramp reroute from drive 61 (2 min, repeats), through the live path. Watch the right window in Sport. Stop: sh tt.sh stop';;
        route) f=$(routefile "${3:-}"); test -n "$f" || { say 'No such route recording (sh tt.sh routes lists them).'; exit 1; }
               sz=$(ls -l "$f" | awk '{print $5}'); test "$sz" -le 1048576 || { say "$f is over 1 MB: send it to me and I cut the part you want into a replay."; exit 1; }
               cp "$f" /tmp/tt-rgi-play.txt && say "Replaying $f through the live path (once). Stop: sh tt.sh stop";;
        arrows) cp "$K/catalogue.txt" /tmp/tt-rgi-play.txt && say 'Arrow catalogue playing (about 3 min). Stop: sh tt.sh stop';;
        *) say 'Usage: sh tt.sh play route [N] | reroute | map | new | fit | win | kdk | panel | komo | stock | arrows'; exit 2;;
    esac
    ;;
stop)
    rm -f /tmp/tt-rgi-play.txt; say 'Playback stopped; iPhone feed resumes in a few seconds.'
    ;;
test)
    # One session, one SSH connection, paced by you: each step waits for Enter, so steps can
    # be skipped or taken slowly. q (then Enter) at any prompt stops, cleans up and saves.
    PL=/tmp/tt-rgi-play.txt
    UM=/tmp/mibr-alt111-url-mode
    DIRECT=/tmp/tt-kdk-direct
    finish(){ rm -f "$PL" "$UM" "$DIRECT"; say ''; say '== Saving the logs.'; sh "$0" save; say 'Done. Send the photos (named by step) and the newest logs folder.'; }
    next(){ echo "      [Enter] next step, q = stop and save"; read a; case "$a" in q|Q) finish; exit 0;; esac; }
    step(){ say ''; say "== $1"; }
    rm -f /tmp/tt-panel-pos "$DIRECT"
    say 'Guided test. iPhone plugged in, CarPlay running, parked. PHOTO = take one photo.'
    say 'Each step waits for Enter. Skip a step by just pressing Enter.'
    next
    step 'PART 1: parked demo (no route needed). A demo panel (exit 12A with lanes) stays up.'
    cp "$K/place-test.txt" "$PL" || exit 1
    step 'M1 CLASSIC view, MAP tab: CarPlay map + our framed panel. PHOTO'
    next
    step 'M2 CLASSIC view, CAR tab: panel inside the stock frame. PHOTO'
    next
    step 'M3 SPORT view with the MAP: panel over the map, left of the tach. PHOTO'
    next
    step 'M4 SPORT view, trip computer page: panel at its usual spot. PHOTO'
    next
    cp "$K/new-test.txt" "$PL"
    step 'N CLASSIC view, MAP tab: all new pictures cycle (about 75 s for all). PHOTO only if one looks wrong.'
    next
    rm -f "$PL"
    step 'PART 2: live routes. G0: start a GOOGLE MAPS route now, somewhere a few miles away.'
    next
    step 'G1 CLASSIC view, MAP tab. PHOTO'
    next
    step 'G2 CLASSIC view, CAR tab. PHOTO'
    next
    step 'G3 SPORT view with the MAP. PHOTO'
    next
    step 'G4 SPORT view, trip computer. PHOTO'
    next
    step 'A0: end the Google route, start an APPLE MAPS route now.'
    next
    step 'A1 CLASSIC view, MAP tab. PHOTO'
    next
    step 'A2 SPORT view with the MAP. PHOTO'
    next
    step 'PART X: experiment, keep the Apple route running. Banner window opened directly, nav engine left out.'
    touch "$DIRECT"
    step 'X1 SPORT view with the MAP: CarPlay map AND the panel on the right? PHOTO'
    next
    step 'X2 CLASSIC view, MAP tab: CarPlay map still there? PHOTO'
    next
    step 'X3 CLASSIC view, CAR tab: panel in its window? PHOTO'
    next
    rm -f "$DIRECT"
    step 'Experiment off (back to normal).'
    step 'PART 3: the iPhone draws its own maneuver card on the map. Try switching it off.'
    echo map > "$UM"
    step 'C0: UNPLUG the iPhone, wait 5 s, PLUG it back in, wait for CarPlay, start any route. Then Enter.'
    next
    step 'C1 CLASSIC view, MAP tab: is the iPhone card (top left of the map) gone? Map still there? PHOTO'
    next
    step 'C2 SPORT view with the MAP. PHOTO'
    next
    rm -f "$UM"
    step 'END: end the route. (Unplug and replug once more to get the iPhone card back.)'
    finish
    ;;
layers)
    # Parked test of the cockpit layer lists (dmdt). Context 89 = map + banner; its layer
    # list is changed in the display manager's memory only (no flash; an MMI restart or
    # the end of this test puts the stock list back).
    DIRECT=/tmp/tt-kdk-direct
    S89=
    finish(){
        if test -n "$S89"; then dm dc 89 $S89 >/dev/null 2>&1 && say "Context 89 back to stock: $S89"; fi
        rm -f "$DIRECT" /tmp/tt-layer-off; dmdump 'end'
        say ''; say '== Saving the logs.'; sh "$0" save; say 'Done. Send the photos (named by step) and the newest logs folder.'
    }
    next(){ echo "      [Enter] next step, q = stop and save"; read a; case "$a" in q|Q) finish; exit 0;; esac; }
    step(){ say ''; say "== $1"; }
    try(){
        # $1 = name, rest = layer list for context 89
        n=$1; shift
        if dm dc 89 "$@" >/dev/null 2>&1; then
            echo "89 = $*" >> /tmp/tt-dmdt.txt
            step "$n: context 89 is now: $* (stock: $S89)"
        else
            step "$n: dmdt refused '$*' - just press Enter"; next; return
        fi
        say "   CLASSIC view: tap the CAR tab, then the MAP tab again (that applies it)."
        say "   $n-a CLASSIC view, MAP tab: CarPlay map? panel? PHOTO"
        next
        say "   $n-b SPORT view with the map: CarPlay map? panel on the right? PHOTO"
        next
        dmdump "$n"
    }
    test -x /eso/bin/apps/dmdt || { say 'dmdt not found on this unit.'; exit 1; }
    rm -f /tmp/tt-rgi-play.txt "$DIRECT"
    touch /tmp/tt-layer-off   # v37: keep the jar's own layer lists out of the way
    say 'Layer test. Parked, CarPlay running. About 10 steps, Enter for each, q stops (stock layers come back).'
    step 'L0: start any route on the iPhone (parked is fine). CLASSIC view, MAP tab. CarPlay map showing? Then Enter.'
    next
    dmdump 'L0 map, banner off (context 10)'
    touch "$DIRECT"
    step 'L1: banner window opened (experiment mode). Stock map should now cover CarPlay. PHOTO'
    next
    dmdump 'L1 banner on (context 89)'
    # Stock list from the HMI (defineContextsForG24); never read back from the unit, because
    # an interrupted earlier run can leave a test list in memory.
    S89="20 16 19"
    say "   context 10 = $(ctxlist 10)   context 89 now = $(ctxlist 89)   (stock 89 = $S89)"
    try LA 20 16
    try LB 16 19 20
    try LC 20 16 60
    try LD 16 60 20
    try LE 20 60 16
    dm dc 89 $S89 >/dev/null 2>&1 && say "Context 89 back to stock: $S89"; S89=
    # Photo 3 of the v36 test: right after the window closed, the Sport view showed the
    # CarPlay map AND the panel in the right window. Try to catch that state on purpose.
    step 'LZ: SPORT view with the map. Tap CAR, then MAP (window open, stock map). Then Enter: the window closes.'
    next
    rm -f "$DIRECT"; sleep 1; dmdump 'LZ 1 s after close'
    step 'LZ-a right after closing: CarPlay map AND panel on the right? PHOTO now, and again after 10 s.'
    next
    dmdump 'LZ later'
    finish
    ;;
check)
    # v37 guided check, paced by Enter (q stops and saves).
    finish(){ dmdump 'check end'; say ''; say '== Saving the logs.'; sh "$0" save; say 'Done. Send the photos (named by step) and the newest logs folder.'; }
    next(){ while :; do echo "      [Enter] next step, c = mark a CarPlay crash now, q = stop and save"; read a; case "$a" in q|Q) finish; exit 0;; c|C) mark "crash (step $S)";; *) return;; esac; done; }
    step(){ S=${1%%:*}; S=${S%% *}; say ''; say "== $1"; }
    rm -f /tmp/tt-rgi-play.txt /tmp/tt-kdk-direct /tmp/tt-layer-off /tmp/tt-rgi-kdk-map /tmp/tt-panel-pos
    say "v39 check. jar: $(which_jar). Parked, iPhone plugged in, CarPlay running."
    say '   If the CarPlay tab goes black at any time: type c and Enter right away (marks the logs).'
    say '   If the CarPlay icon only flickers after a replug: q, then sh tt.sh diag, then restart the MMI.'
    say "   layer lists now: map 10 = $(ctxlist 10)   map+banner 89 = $(ctxlist 89)"
    grep 'layers:' /tmp/tt-rgd.log 2>/dev/null | tail -2
    step 'P0: start a route on the iPhone (Google or Apple, parked is fine). Then Enter.'
    next
    dmdump 'P0 route on'
    step 'P1 CLASSIC view, MAP tab: no stock straight-arrow before the first turn; then CarPlay map + panel in the stock frame? PHOTO'
    next
    step 'P2 CLASSIC view, CAR tab: panel in the stock frame. PHOTO'
    next
    step 'P3 SPORT view with the map: CarPlay map + panel on the right? PHOTO'
    next
    step 'P4 SPORT view, trip computer: panel on the right. PHOTO'
    next
    dmdump 'P4'
    step 'P5: END the route. CLASSIC view, MAP tab: banner gone within about 3 s, CarPlay map still there? PHOTO'
    next
    step 'P6: UNPLUG the iPhone, wait 15 s, do NOT touch the tabs. MAP tab: STOCK map (not black)? PHOTO'
    next
    dmdump 'P6 phone off'
    say "   layer lists now: map 10 = $(ctxlist 10)   map+banner 89 = $(ctxlist 89)"
    step 'P7: PLUG the iPhone back in, wait for CarPlay, do NOT touch the tabs. MAP tab: CarPlay map back by itself? PHOTO'
    next
    step 'P8: flip CAR tab -> CarPlay tab -> CAR tab a few times. If CarPlay goes black: type c, then note how long you waited.'
    next
    dmdump 'P7 phone back'
    say "   layer lists now: map 10 = $(ctxlist 10)   map+banner 89 = $(ctxlist 89)"
    finish
    ;;
hookfix)
    # CarPlay hook TT build 2 (carplay/src/native, scripts/wsl-native-build.sh tt): h320 safe
    # area, listening sockets close-on-exec (no busy port after a crash), stock delegates first;
    # build 2 adds the RAM-only test overrides of sh tt.sh look.
    # Writes one file on flash, keeps the original next to the kit. Takes effect after an MMI restart.
    cur=$("$T" "$HOOK" 2>/dev/null)
    case "${2:-}" in
        undo)
            src="$K/libaltscreen111.so.orig"; test -f "$src" || { say 'Nothing to undo.'; exit 1; }
            want=$("$T" "$src" 2>/dev/null)
            case "$want" in $HOOK_STOCK|$HOOK_H320) ;; *) say 'Saved original is damaged - nothing changed.'; exit 1;; esac;;
        "")
            case "$cur" in
                $HOOK_STOCK|$HOOK_H320|$FIX_STOCK|$FIX_H320|$HOOK_TT1) src="$K/libaltscreen111-tt2.so"; want=$HOOK_TT2;;
                $HOOK_TT2) say 'Already in place.'; exit 0;;
                *) say "Unknown hook on the car ($cur) - nothing changed."; exit 1;;
            esac
            test -f "$src" || { say "missing $src - copy the kit and run setup again."; exit 1; }
            "$T" $want "$src" || { say "$src is damaged - nothing changed."; exit 1; };;
        *) say 'Usage: sh tt.sh hookfix   |   sh tt.sh hookfix undo'; exit 2;;
    esac
    test "$cur" = "$want" && { say 'Already in place.'; exit 0; }
    rw
    case "$cur" in $HOOK_STOCK|$HOOK_H320) test -f "$K/libaltscreen111.so.orig" || cp "$HOOK" "$K/libaltscreen111.so.orig";; esac
    # new file + rename: the running process keeps the old file, the next start loads the new one
    cp "$src" "$HOOK.new" && chmod 755 "$HOOK.new" && mv "$HOOK.new" "$HOOK"
    "$T" $want "$HOOK" && r=0 || r=1
    ro
    test $r = 0 && say 'Done. Restart the MMI (hold the MMI knob) to load it.' || say 'Copy failed - check with: sh tt.sh report'
    ;;
sup)
    # Supervisor v7 (scripts/mksup.py): the cockpit renderer runs for the whole session (started
    # after 3 s of car screen, never stopped for the CarPlay tab), no display switching.
    # One file on flash, v2 kept; next replug.
    cur=$("$T" "$SUP" 2>/dev/null)
    case "${2:-}" in
        undo) src="$K/cluster_supervisor.sh.v2"; want=$SUP_V2; test -f "$src" || { say 'Nothing to undo.'; exit 1; };;
        v6) src="$K/cluster_supervisor-v6.sh"; want=$SUP_V6
            case "$cur" in $SUP_V2|$SUP_V3|$SUP_V4|$SUP_V5|$SUP_V6|$SUP_V7) ;; *) say "Unknown supervisor on the car ($cur) - nothing changed."; exit 1;; esac;;
        ""|v7) src="$K/cluster_supervisor-v7.sh"; want=$SUP_V7
            case "$cur" in $SUP_V2|$SUP_V3|$SUP_V4|$SUP_V5|$SUP_V6|$SUP_V7) ;; *) say "Unknown supervisor on the car ($cur) - nothing changed."; exit 1;; esac;;
        *) say 'Usage: sh tt.sh sup [v7]  |  sh tt.sh sup v6  |  sh tt.sh sup undo'; exit 2;;
    esac
    test "$cur" = "$want" && { say 'Already in place.'; exit 0; }
    "$T" $want "$src" || { say "$src is missing or damaged - nothing changed."; exit 1; }
    rw
    test "$cur" = "$SUP_V2" -a ! -f "$K/cluster_supervisor.sh.v2" && cp "$SUP" "$K/cluster_supervisor.sh.v2"
    cp "$src" "$SUP.new" && chmod 755 "$SUP.new" && mv "$SUP.new" "$SUP"
    "$T" $want "$SUP" && r=0 || r=1
    ro
    test $r = 0 && say 'Done. Unplug the iPhone, wait 10 s, plug it back in.' || say 'Copy failed - check with: sh tt.sh report'
    ;;
crash)
    # v41 + supervisor v5 check: tab flips, map return speed, unplug and replug.
    finish(){ rm -f /tmp/mu1462-keep-renderer; mark 'crash test end'; say ''; say '== Saving the logs.'; sh "$0" save; say 'Done. Send the newest logs folder.'; }
    next(){ while :; do echo "      [Enter] next step, c = something went wrong just now, q = stop and save"; read a; case "$a" in q|Q) finish; exit 0;; c|C) mark "problem (step $S)";; *) return;; esac; done; }
    step(){ S=${1%%:*}; S=${S%% *}; say ''; say "== $1"; mark "step $S"; }
    say "Check. $(sh "$0" report 2>/dev/null | grep -E 'jar:|supervisor')"
    say 'Parked, CarPlay connected, no route needed. c + Enter whenever something goes wrong (black, crash, slow).'
    step 'Q: CAR tab <-> CarPlay tab, quick flips, 6 times. Any black CarPlay tab or crash?'
    next
    step 'M: MAP view <-> CarPlay tab, 10 times, some FAST. CarPlay tab always shows CarPlay (not the map)? Map back at once? With a route too if you can.'
    next
    step 'U1: on the MAP view, UNPLUG the iPhone. Within about 5 s: stock map (not black)? PHOTO if not.'
    next
    step 'U2: PLUG it back in, stay on the MAP view. CarPlay connects (no flicker) and the CarPlay map comes back?'
    next
    finish
    ;;
card)
    # The iPhone's own maneuver card on the cockpit CarPlay map.
    #   off            (default, tt-zoom v56) map only
    #   auto           card near turns (1 km, motorway 2 km, 10 s at route start, 5 s after a turn);
    #                  never in the Sport skin's small view. RAM only: an MMI restart goes back to off
    #   on             card the whole time, in both views
    #   left|right|top card position when it shows (small settings file on flash)
    #   default        auto, hook default position
    # Changes go to the iPhone at once when CarPlay is connected (the hook re-sends showUI).
    LAYOUT=/mnt/app/root/mibr-carplay111-nav.maneuverLayout
    send(){ touch /tmp/mibr-alt111-show-only; }
    case "${2:-}" in
        auto) rm -f /tmp/tt-card-fixed; touch /tmp/tt-card-auto; say 'Card: near turns (switches within a few seconds).';;
        off) rm -f /tmp/tt-card-auto; touch /tmp/tt-card-fixed; echo map > /tmp/mibr-alt111-url-mode; send; say 'Card off (map only) in both views.';;
        on) rm -f /tmp/tt-card-auto; touch /tmp/tt-card-fixed; echo base > /tmp/mibr-alt111-url-mode; send; say 'Card on in both views.';;
        left|right|top)
            rw; echo "$2" > "$LAYOUT"; ro; send
            say "Card position $2 (whenever the card shows).";;
        default)
            rm -f /tmp/tt-card-fixed /tmp/tt-card-auto
            echo map > /tmp/mibr-alt111-url-mode
            if test -f "$LAYOUT"; then rw; rm -f "$LAYOUT"; ro; fi
            send
            say 'Card: off (map only), hook default position.';;
        *) say "Usage: sh tt.sh card off | auto | on | left | right | top | default"
           say "   now: $(test -e /tmp/tt-card-auto && echo auto || echo fixed), surface $(cat /tmp/mibr-alt111-url-mode 2>/dev/null || echo base), position $(cat $LAYOUT 2>/dev/null || echo default)"
           exit 2;;
    esac
    ;;
strip)
    # The cockpit map's bottom strip during a CarPlay route (tt-zoom v54). RAM only, next update.
    #   turn     (default) near the turn: "Left to Alpha St"; otherwise the current street
    #   always   the next turn ("Left to Alpha St") the whole route, instead of the current street
    #   plain    street names only (before v54)
    case "${2:-}" in
        turn) rm -f /tmp/tt-strip; say 'Strip: next turn near the turn, current street otherwise.';;
        always|plain) echo "$2" > /tmp/tt-strip; say "Strip: $2.";;
        *) say 'Usage: sh tt.sh strip turn | always | plain'
           say "   now: $(cat /tmp/tt-strip 2>/dev/null || echo turn)"
           exit 2;;
    esac
    ;;
autozoom)
    # Zoom the cockpit CarPlay map in near a turn and out after it (tt-zoom v47), through the
    # steering-wheel zoom path. RAM only.
    #   on | off     (default on) zoom in 330 m before a turn (motorway 800 m), out 5 s after it
    #   steps N      how far, 1-6 zoom steps (default 2)
    #   memory on|off  (tt-zoom v53, default on) re-apply your zoom after the iPhone resets the map
    #                (card switch, reconnect)
    case "${2:-}" in
        memory) case "${3:-}" in
               on) rm -f /tmp/tt-zoom-noremember; say 'Zoom memory on.';;
               off) touch /tmp/tt-zoom-noremember; say 'Zoom memory off.';;
               *) say 'Usage: sh tt.sh autozoom memory on | off'; exit 2;; esac;;
        on) rm -f /tmp/tt-autozoom-off; say 'Auto zoom on.';;
        off) touch /tmp/tt-autozoom-off; say 'Auto zoom off.';;
        steps) case "${3:-}" in [1-6]) echo "$3" > /tmp/tt-autozoom-steps; say "Auto zoom: $3 steps.";;
               *) say 'Usage: sh tt.sh autozoom steps 1-6'; exit 2;; esac;;
        *) say 'Usage: sh tt.sh autozoom on | off | steps N'
           say "   now: $(test -e /tmp/tt-autozoom-off && echo off || echo on), $(cat /tmp/tt-autozoom-steps 2>/dev/null || echo 2) steps (log: /tmp/tt-zoom.log)"
           exit 2;;
    esac
    ;;
shot)
    # A screenshot of the cockpit as the head unit draws it (tt-zoom tele18): the head unit writes
    # the PNG itself, through the same call as its own key-combo screenshot. Copy it off with scp.
    f=/tmp/tt-shot-$(date +%H%M%S 2>/dev/null || echo 0).png
    echo "$f" > /tmp/tt-shot
    say "Screenshot requested ($f); waiting..."
    n=0; while test $n -lt 10 && ! test -s "$f"; do sleep 1; n=$((n+1)); done
    if test -s "$f"; then say "Saved $f ($(ls -l "$f" | awk '{print $5}') bytes)."; else say 'No file yet: grep "tt shot" /tmp/tt-rgd.log'; fi
    ;;
tele)
    # The view's receiver needs UDP 49160. ttlink (the link-test tool) binds the same port; only one
    # can hold it, so the view stops a running ttlink. Nothing else is touched.
    tele_free(){
        if pidin ar 2>/dev/null | grep -v grep | grep -q ttlink; then
            slay -f ttlink 2>/dev/null; say 'Stopped ttlink (it held UDP 49160, which the view needs).'
        fi
    }
    # OpenHaldex autocross view (tt-zoom tele23, wiki Telemetry.md): our own full-screen picture over
    # the map tab (Classic) or the small view's map area (Sport). RAM switches, read within 1 s;
    # "boot" writes the one flash default.
    case "${2:-}" in
        on) rm -f /tmp/tt-tele-off; touch /tmp/tt-tele-on; tele_free; say 'Autocross view on (shows on the map tab).';;
        off) rm -f /tmp/tt-tele-on; touch /tmp/tt-tele-off; say 'Autocross view off.';;
        demo) case "${3:-on}" in
               on) touch /tmp/tt-tele-demo; say 'Demo values on: sweeping fake data, no OpenHaldex needed.';;
               off) rm -f /tmp/tt-tele-demo; tele_free; say 'Demo values off: live OpenHaldex data.';;
               *) say 'Usage: sh tt.sh tele demo on | off'; exit 2;; esac;;
        rot) case "${3:-}" in
               deg|-deg|rad|-rad|off) echo "$3" > /tmp/tt-tele-rot; say "Tilted text and icons: $3";;
               *) say 'Usage: sh tt.sh tele rot deg | -deg | rad | -rad | off   (default -deg, right on the car)'
                  exit 2;; esac;;
        beep) case "${3:-}" in
               ""|-h) say 'Usage: sh tt.sh tele beep N [system|ring] | default [system|ring] | stop'
                      say '   plays the head unit tone N once (view must be on); delay in: grep beep /tmp/tt-rgd.log'; exit 2;;
               *) echo "$3 ${4:-system}" > /tmp/tt-tele-beep; say "Tone $3 (${4:-system}) requested; within 1 s. Log: grep beep /tmp/tt-rgd.log | tail";; esac;;
        shiftbeep) case "${3:-}" in
               off) rm -f /tmp/tt-tele-shiftbeep; say 'Shift tone off.';;
               ""|-h) say 'Usage: sh tt.sh tele shiftbeep N [system|ring] | off   (plays tone N each time rpm rises past 6500)'; exit 2;;
               *) echo "$3 ${4:-system}" > /tmp/tt-tele-shiftbeep; say "Shift tone: tone $3 (${4:-system}) at the shift point. Off: sh tt.sh tele shiftbeep off";; esac;;
        sounds) say 'Sound files on the head unit (also saved to /tmp/tt-sounds.txt):'
                find /mnt /eso /fs -type f \( -name '*.wav' -o -name '*.WAV' -o -name '*.pcm' -o -name '*.mp3' -o -name '*.ogg' \) > /tmp/tt-sounds.txt 2>/dev/null
                cat /tmp/tt-sounds.txt | head -60; say "$(wc -l < /tmp/tt-sounds.txt) files";;
        ui) case "${3:-}" in
               open|up|down|press|back|close) echo "$3" > /tmp/tt-tele-ui; say "Control: $3";;
               page) echo page > /tmp/tt-tele-ui; say 'Page: live view <-> Haldex page';;
               map) case "${4:-}" in
                      "") say 'Usage: sh tt.sh tele ui map "CODE EVENT ACTION" ...   (EVENT pressed|turned|long; ACTION open|up|down|press|back)'; exit 2;;
                      *) shift 3; : > /tmp/tt-tele-keymap; for l in "$@"; do echo "$l" >> /tmp/tt-tele-keymap; done; say "Key map (RAM):"; cat /tmp/tt-tele-keymap;; esac;;
               savemap) if test -f /tmp/tt-tele-keymap; then rw; cp /tmp/tt-tele-keymap $K/tele-keymap; ro; say 'Key map saved to flash.'; else say 'No key map in RAM: sh tt.sh tele ui map ... first.'; exit 1; fi;;
               *) say 'Usage: sh tt.sh tele ui open | up | down | press | back | close | map "CODE EVENT ACTION"... | savemap'; exit 2;; esac;;
        glide) case "${3:-}" in
               on) touch /tmp/tt-tele-glide; say 'Glide on: gauges move smoothly between data updates (about 50 ms behind).';;
               off) rm -f /tmp/tt-tele-glide; if test -f $K/tele-glide; then rw; rm -f $K/tele-glide; ro; fi; say 'Glide off.';;
               boot) rw; touch $K/tele-glide; ro; say 'Glide on at every start.';;
               *) say 'Usage: sh tt.sh tele glide on | off | boot'; exit 2;; esac;;
        car|pictures) for f in hx-car-up hx-car-side strip-track strip-lit strip-red; do
               if test -f /tmp/$f.rgba; then rw; cp /tmp/$f.rgba $K/$f.rgba; ro; say "Installed $f.rgba."; fi
             done; say 'Car and strip pictures installed: restart the MMI to load them.';;
        theme) case "${3:-}" in
               on) if test -f $K/theme-classic.rgba -o -f $K/theme-sport.rgba; then touch /tmp/tt-tele-theme; say 'Theme on (within 1 s).'
                   else say "No theme pictures in $K: copy theme-classic.rgba and theme-sport.rgba there first."; exit 1; fi;;
               off) rm -f /tmp/tt-tele-theme; if test -f $K/tele-theme; then rw; rm -f $K/tele-theme; ro; fi; say 'Theme off.';;
               boot) rw; touch $K/tele-theme; ro; say 'Theme on at every start.';;
               install) for v in classic sport; do
                          if test -f /tmp/theme-$v.rgba; then rw; cp /tmp/theme-$v.rgba $K/theme-$v.rgba; ro; say "Installed theme-$v.rgba."; fi
                        done;;
               *) say 'Usage: sh tt.sh tele theme on | off | boot | install   (install copies /tmp/theme-*.rgba to the flash)'; exit 2;; esac;;
        selftest)
            # One parked test of everything in the autocross view (wiki Telemetry.md "Selftest").
            # Each step says what to watch; Enter moves on. Everything lands in one file to send back.
            OUT=/tmp/tt-selftest-$(date +%H%M%S 2>/dev/null || echo run).txt
            st_log(){ echo "== $*" >> $OUT; }
            st_grab(){ grep "$1" /tmp/tt-rgd.log 2>/dev/null | tail -${2:-40} >> $OUT; }
            st_wait(){ say "$*"; say '   ... press Enter when done'; read _x; }
            : > $OUT
            st_log "selftest $(date 2>/dev/null)"
            st_log jar; $T /mnt/app/eso/hmi/lsd/jars/tt-zoom.jar >> $OUT 2>&1
            say 'Selftest: park, engine running or ignition on, cockpit on the MAP tab.'
            rm -f /tmp/tt-tele-off /tmp/tt-tele-probe /tmp/tt-tele-shiftbeep; touch /tmp/tt-tele-on /tmp/tt-tele-demo; tele_free
            st_wait '1/7 View on with demo values. Note how long until it draws (Classic).'
            st_log 'step 1 start and build'; st_grab 'tele' 40
            st_wait '2/7 Switch Sport -> Classic -> Sport -> Classic with the View button, 3 times. Note if Classic still lags.'
            st_log 'step 2 switching'; st_grab 'built\|tele job\|tele stats' 40
            st_wait '3/7 Watch the demo sweep pass 6500 a few times: strip, shift lights (and Sport frame) flash together? Any freeze?'
            st_log 'step 3 redline'; st_grab 'tele stats\|lost' 20
            say '4/7 Sound files on the head unit:'
            find /mnt /eso /fs -type f \( -name '*.wav' -o -name '*.WAV' -o -name '*.pcm' -o -name '*.mp3' -o -name '*.ogg' \) 2>/dev/null > /tmp/tt-sounds.txt
            st_log 'step 4 sounds'; cat /tmp/tt-sounds.txt >> $OUT; say "   $(wc -l < /tmp/tt-sounds.txt) files listed"
            for n in 1 2 3 4 5 6 7 8; do
                echo "$n system" > /tmp/tt-tele-beep; say "   tone $n (system)"; sleep 3
            done
            echo "default system" > /tmp/tt-tele-beep; say '   default tone (system)'; sleep 3
            echo "1 ring" > /tmp/tt-tele-beep; say '   tone 1 (ring player)'; sleep 4
            st_wait '   Which tone numbers did you hear? (write them down)'
            st_log 'step 4 beeps'; st_grab 'beep' 60
            touch /tmp/tt-tele-keys
            st_wait '5/7 Key trace on: press each steering-wheel button (scroll wheel press, roll up/down, view, menu), slowly, one by one.'
            rm -f /tmp/tt-tele-keys; st_log 'step 5 keys'; st_grab 'tele key' 80
            echo grp > /tmp/tt-tele-probe
            st_wait '6/7 Strip probe: does the lit strip end at the white marks while the demo sweeps? Photo of the text line please.'
            echo img > /tmp/tt-tele-probe
            st_wait '   Same with the clip on the picture (img mode): better, worse, same?'
            rm -f /tmp/tt-tele-probe; st_log 'step 6 probe'; st_grab 'probe' 30
            echo "1 system" > /tmp/tt-tele-shiftbeep
            st_wait '7/7 Shift tone on tone 1: does it sound each time the sweep passes 6500? Late or on time?'
            rm -f /tmp/tt-tele-shiftbeep; st_log 'step 7 shift tone'; st_grab 'shift tone\|beep' 30
            rm -f /tmp/tt-tele-demo
            st_log 'tail'; tail -80 /tmp/tt-rgd.log >> $OUT 2>/dev/null
            say "Done. Send me $OUT (scp it like the jar, the other way) and your notes."
            ;;
        keys) case "${3:-}" in
               trace) touch /tmp/tt-tele-keys; say 'Key trace on: press the wheel buttons, then: grep "tele key" /tmp/tt-rgd.log | tail -30';;
               notrace) rm -f /tmp/tt-tele-keys; say 'Key trace off.';;
               toggle) case "${4:-}" in
                         [0-9]*) echo "$4" > /tmp/tt-tele-key-code; say "Double press of key $4 now switches the view (until restart; flash: sh tt.sh tele keys save $4).";;
                         *) say 'Usage: sh tt.sh tele keys toggle CODE'; exit 2;; esac;;
               save) case "${4:-}" in
                         [0-9]*) rw; echo "$4" > $K/tele-key-code; ro; echo "$4" > /tmp/tt-tele-key-code; say "Key $4 saved as the view toggle (every start).";;
                         *) say 'Usage: sh tt.sh tele keys save CODE'; exit 2;; esac;;
               off) rm -f /tmp/tt-tele-key-code; rw; rm -f $K/tele-key-code; ro; say 'No toggle key.';;
               *) say 'Usage: sh tt.sh tele keys trace | notrace | toggle CODE | save CODE | off'; exit 2;; esac;;
        probe) case "${3:-on}" in
               on|grp) echo grp > /tmp/tt-tele-probe; say 'Strip probe on (clip on a group). For the sweep: sh tt.sh tele demo on';;
               img) echo img > /tmp/tt-tele-probe; say 'Strip probe on (clip on the picture). For the sweep: sh tt.sh tele demo on';;
               off) rm -f /tmp/tt-tele-probe; say 'Strip probe off: the normal view again.';;
               *) say 'Usage: sh tt.sh tele probe on | img | off'; exit 2;; esac;;
        boot) case "${3:-}" in
               on) rw; touch $K/tele-on; ro; say 'Autocross view on at every MMI start.';;
               off) rw; rm -f $K/tele-on; ro; say 'Autocross view no longer on at start.';;
               *) say 'Usage: sh tt.sh tele boot on | off'; exit 2;; esac;;
        *) say 'Usage: sh tt.sh tele on | off | demo on|off | ui open|up|down|press|back|page | glide on|off|boot | pictures | theme on|off|boot|install | selftest | keys trace|toggle N|save N | beep N | shiftbeep N|off | sounds | probe on|img|off | rot deg|-deg|rad|-rad|off | boot on|off'
           say "   now: $(test -e /tmp/tt-tele-on && echo on || (test -e $K/tele-on && ! test -e /tmp/tt-tele-off && echo on || echo off)), demo $(test -e /tmp/tt-tele-demo && echo on || echo off), probe $(cat /tmp/tt-tele-probe 2>/dev/null || echo off), tilt $(cat /tmp/tt-tele-rot 2>/dev/null || echo -deg)"
           exit 2;;
    esac
    ;;
banner)
    # Our turn banner on the CarPlay map (tt-zoom v45/v46). RAM only, applies within 2 s.
    #   always     (default) shown the whole route
    #   auto       shown when the next turn is within 1 km (motorway exits 2 km), for 10 s at
    #              route start and 5 s after a turn; faded out in between
    #   alpha N    background opacity on the CarPlay map, 0-100 (default 95)
    case "${2:-}" in
        auto) touch /tmp/tt-panel-auto; say 'Banner: only near turns.';;
        always) rm -f /tmp/tt-panel-auto; say 'Banner: always shown.';;
        alpha) case "${3:-}" in [0-9]|[0-9][0-9]|100) echo "$3" > /tmp/tt-panel-alpha; say "Banner background: $3% on the CarPlay map.";;
                *) say 'Usage: sh tt.sh banner alpha 0-100'; exit 2;; esac;;
        *) say 'Usage: sh tt.sh banner auto | always | alpha N'
           say "   now: $(test -e /tmp/tt-panel-auto && echo auto || echo always), background $(cat /tmp/tt-panel-alpha 2>/dev/null || echo 95)%"
           exit 2;;
    esac
    ;;
eta)
    # The phone app's own ETA bar on the cockpit map (user choice 2026-11-20: off; the car's bottom
    # strip shows the same). One small settings file on flash; sent to the iPhone at once.
    F=/mnt/app/root/mibr-carplay111-nav.showETA
    case "${2:-}" in
        off) rw; echo no > $F; ro; touch /tmp/mibr-alt111-show-only; say 'App ETA bar off.';;
        on) if test -f $F; then rw; rm -f $F; ro; fi; touch /tmp/mibr-alt111-show-only; say 'App ETA bar on.';;
        *) say "Usage: sh tt.sh eta on | off   (now: $(test "$(cat $F 2>/dev/null)" = no && echo off || echo on))"; exit 2;;
    esac
    ;;
look)
    # How much road the cockpit CarPlay map shows (hook TT build 2). RAM only, read by the hook
    # when the iPhone connects: replug after a change; an MMI restart forgets it.
    #   1..4           screen size the iPhone is told: 1 = 320x120 mm (built in), 2 = 430x160,
    #                  3 = 580x215, 4 = 780x290. A bigger screen should make iOS draw smaller.
    #   mm W H         any size
    #   safe X Y W H   the safe window (built in 520 110 400 320)
    #   reset          built-in values
    test "$("$T" "$HOOK" 2>/dev/null)" = "$HOOK_TT2" || say 'Note: needs hook TT build 2 (sh tt.sh hookfix) - the current hook ignores this.'
    MM=/tmp/tt-hook-mm; SAFE=/tmp/tt-hook-safe
    case "${2:-}" in
        1) rm -f $MM;;
        2) echo '430 160' > $MM;;
        3) echo '580 215' > $MM;;
        4) echo '780 290' > $MM;;
        mm) test -n "${4:-}" || { say 'Usage: sh tt.sh look mm W H'; exit 2; }; echo "$3 $4" > $MM;;
        safe) test -n "${6:-}" || { say 'Usage: sh tt.sh look safe X Y W H'; exit 2; }; echo "$3 $4 $5 $6" > $SAFE;;
        reset) rm -f $MM $SAFE;;
        # v51: safe window lower/taller: the car sits lower during guidance, the app's ETA bar lower
        low1) echo '520 110 400 360' > $SAFE;;
        low2) echo '520 110 400 400' > $SAFE;;
        low3) echo '520 170 400 360' > $SAFE;;
        *) say 'Usage: sh tt.sh look 1|2|3|4 | low1|low2|low3 | mm W H | safe X Y W H | reset'
           say "   now: mm $(cat $MM 2>/dev/null || echo 320 120), safe $(cat $SAFE 2>/dev/null || echo 520 110 400 320)"
           exit 2;;
    esac
    say "Screen $(cat $MM 2>/dev/null || echo 320 120) mm, safe window $(cat $SAFE 2>/dev/null || echo 520 110 400 320). Unplug the iPhone, wait 5 s, plug it back in."
    ;;
unstick)
    # After a crash the next connections fail ("cannot bind renderer tee port"): something from
    # the dead session still holds 127.0.0.1:19820. Record who, then stop leftover supervisor /
    # renderer processes whose session (dio_manager PID, their last argument) is gone.
    sh "$0" diag >/dev/null 2>&1
    n=0
    stopped=
    pidin ar 2>/dev/null | grep -E 'cluster_supervisor.sh|cluster_runtime' | grep -v grep > /tmp/tt-unstick.$$
    while read -r pid rest; do
        owner=${rest##* }
        case "$owner" in ''|*[!0-9]*) continue;; esac
        if ! kill -0 "$owner" 2>/dev/null; then
            say "stopping $pid ($rest): its session $owner is gone"
            kill -TERM "$pid" 2>/dev/null; n=$((n + 1)); stopped="$stopped $pid"
        fi
    done < /tmp/tt-unstick.$$
    if test $n -gt 0; then
        sleep 2
        for pid in $stopped; do kill -0 "$pid" 2>/dev/null && kill -KILL "$pid" 2>/dev/null && say "forced $pid"; done
    fi
    rm -f /tmp/tt-unstick.$$
    d=$(pidin ar 2>/dev/null | grep -c '[d]io_manager')
    say "Stopped $n leftover process(es). dio_manager processes running: $d"
    test "$d" -gt 1 && say 'More than one dio_manager: an old session did not end - send the logs (sh tt.sh save).'
    { echo "===== UNSTICK stopped=$n dio_manager=$d"; } >> /tmp/tt-diag.txt
    say 'Now unplug the iPhone, wait 10 s, plug it back in. If it still flickers: sh tt.sh save, then restart the MMI.'
    ;;
delay)
    # Seconds the cockpit renderer waits on the map view before starting (supervisor v5 reads it
    # at the next connect; lost at an MMI restart). Longer = fewer crashes, slower map return.
    case "${2:-}" in
        ''|*[!0-9]*) say "Usage: sh tt.sh delay SECONDS   (now: $(cat /tmp/mu1462-start-delay 2>/dev/null || echo 1))"; exit 2;;
    esac
    echo "$2" > /tmp/mu1462-start-delay
    say "Renderer start delay on the map view: $2 s from the next connect (replug)."
    ;;
diag)
    # When the CarPlay icon only flickers: who holds the hook's ports? Added to the logs.
    { echo "===== DIAG"; echo '--- processes'; pidin arg 2>/dev/null | grep -E 'dio_manager|cluster_|mm-ipod|sleep' | grep -v grep
      echo '--- sockets'; netstat -an 2>/dev/null | grep -E '19820|19821|6031|19810'
      echo '--- hook'; tail -20 /tmp/altscreen111.log 2>/dev/null
      echo '--- renderer'; tail -10 /tmp/mu1438-cluster-runtime.log 2>/dev/null
      for p in $(pidin arg 2>/dev/null | grep -E 'dio_manager|cluster_' | grep -v grep | awk '{print $1}'); do
          echo "--- fds of $p"; pidin -p $p fds 2>/dev/null | head -40; done; } >> /tmp/tt-diag.txt 2>&1
    tail -40 /tmp/tt-diag.txt; say ''; say 'Saved in /tmp/tt-diag.txt (sh tt.sh save keeps it).'
    ;;
mark)
    shift; mark "${*:-crash}"
    ;;
handover)
    # Window handover stage 1c: TT renderer build 3 (hide mode by default, watches the screen
    # itself every 25 ms) + supervisor v9.
    # Writes TWO files on flash (renderer, supervisor). The files from before the first
    # 'handover' stay in the kit folder for 'sh tt.sh handover undo'. Takes effect at the next
    # CarPlay connect (replug).
    rt=$("$T" "$RT" 2>/dev/null); sup=$("$T" "$SUP" 2>/dev/null)
    case "${2:-}" in
        "")
            test "$rt" = "$RT_TT6" && test "$sup" = "$SUP_V9" && { say 'Already in place.'; exit 0; }
            case "$rt" in $RT_SPORT|$RT_STAGE1|$RT_TT|$RT_TT2|$RT_TT3|$RT_TT4|$RT_TT5|$RT_TT6) ;; *) say "Unknown renderer on the car ($rt) - nothing changed."; exit 1;; esac
            case "$sup" in $SUP_V2|$SUP_V3|$SUP_V4|$SUP_V5|$SUP_V6|$SUP_V7|$SUP_V8|$SUP_V9) ;; *) say "Unknown supervisor on the car ($sup) - nothing changed."; exit 1;; esac
            "$T" $RT_TT6 "$K/cluster_runtime-tt6" || { say 'cluster_runtime-tt6 missing or damaged - copy the kit and run setup.'; exit 1; }
            "$T" $SUP_V9 "$K/cluster_supervisor-v9.sh" || { say 'cluster_supervisor-v9.sh missing or damaged - copy the kit and run setup.'; exit 1; }
            rw
            # only the files from before the first handover are kept for undo
            case "$rt" in $RT_TT|$RT_TT2|$RT_TT3|$RT_TT4|$RT_TT5|$RT_TT6) ;; *) test -f "$K/cluster_runtime.before-handover" || cp "$RT" "$K/cluster_runtime.before-handover";; esac
            case "$sup" in $SUP_V8|$SUP_V9) ;; *) test -f "$K/cluster_supervisor.sh.before-handover" || cp "$SUP" "$K/cluster_supervisor.sh.before-handover";; esac
            # renderer first: an old renderer would be killed by the pause signal
            cp "$K/cluster_runtime-tt6" "$RT.new" && chmod 755 "$RT.new" && mv "$RT.new" "$RT"
            cp "$K/cluster_supervisor-v9.sh" "$SUP.new" && chmod 755 "$SUP.new" && mv "$SUP.new" "$SUP"
            "$T" $RT_TT6 "$RT" && "$T" $SUP_V9 "$SUP" && r=0 || r=1
            ro
            test $r = 0 && say 'Done. Unplug the iPhone, wait 10 s, plug it back in. Undo: sh tt.sh handover undo' || say 'Copy failed - check with: sh tt.sh report'
            ;;
        undo)
            test -f "$K/cluster_runtime.before-handover" -o -f "$K/cluster_supervisor.sh.before-handover" || { say 'Nothing to undo.'; exit 1; }
            rw
            # supervisor first: v8/v9 must not pause a renderer that cannot resume
            if test -f "$K/cluster_supervisor.sh.before-handover"; then
                cp "$K/cluster_supervisor.sh.before-handover" "$SUP.new" && chmod 755 "$SUP.new" && mv "$SUP.new" "$SUP"
            fi
            if test -f "$K/cluster_runtime.before-handover"; then
                cp "$K/cluster_runtime.before-handover" "$RT.new" && chmod 755 "$RT.new" && mv "$RT.new" "$RT"
            fi
            ro
            say 'Renderer and supervisor from before the handover tests are back. Unplug the iPhone, wait 10 s, plug it back in.'
            ;;
        *) say 'Usage: sh tt.sh handover   |   sh tt.sh handover undo'; exit 2;;
    esac
    ;;
mode)
    # Renderer pause mode (RAM only). Build 3: hide (default: output off, decoding goes on) |
    # keepkd | close (the video output is closed: slow map return).
    case "${2:-}" in
        hide) rm -f /tmp/mu1438-pause-mode; say 'Pause mode: hide.'; mark "mode hide";;
        keepkd|close) echo "$2" > /tmp/mu1438-pause-mode; say "Pause mode: $2."; mark "mode $2";;
        *) say "Usage: sh tt.sh mode hide | keepkd | close   (now: $(cat /tmp/mu1438-pause-mode 2>/dev/null || echo hide))"; exit 2;;
    esac
    ;;
resumeon)
    # When supervisor v9 resumes the renderer (RAM only):
    #   map (default) when the jar reports a map view | any  on any car screen (faster, no jar wait)
    case "${2:-}" in
        map) rm -f /tmp/mu1462-resume-any; say 'Resume on the map view.';;
        any) touch /tmp/mu1462-resume-any; say 'Resume on any car screen.'; mark "resumeon any";;
        *) say "Usage: sh tt.sh resumeon map | any   (now: $(test -e /tmp/mu1462-resume-any && echo any || echo map))"; exit 2;;
    esac
    ;;
attr)
    # Renderer build 5 attribute test (RAM only, on the map view):
    #   toggle  write byte 60 = 1 (expected: the CarPlay map disappears / comes back)
    #   zero    write byte 60 = 0 (expected: nothing changes)
    #   crop N  crop shift N px with byte 60 = 0 (Sport shift test: 453 or 0)
    #   dump    only log the attributes
    case "${2:-}" in
        toggle|zero|dump) echo "$2" > /tmp/mu1438-attr-cmd;;
        crop) case "${3:-}" in ''|*[!0-9]*) say 'Usage: sh tt.sh attr crop 453|0'; exit 2;; esac; echo "crop$3" > /tmp/mu1438-attr-cmd;;
        *) say 'Usage: sh tt.sh attr toggle | zero | crop N | dump'; exit 2;;
    esac
    sleep 1
    grep 'attr test' /tmp/mu1438-cluster-runtime.log 2>/dev/null | tail -1 | cut -c1-120
    ;;
showdelay)
    # Renderer build 4 (RAM only, next show): show the map only MS ms after the screen change.
    case "${2:-}" in
        off|0) rm -f /tmp/mu1438-show-delay; say 'Show delay off.'; mark "showdelay 0";;
        *[!0-9]*|'') say "Usage: sh tt.sh showdelay MS | off   (now: $(cat /tmp/mu1438-show-delay 2>/dev/null || echo 0))"; exit 2;;
        *) echo "$2" > /tmp/mu1438-show-delay; say "Show delay $2 ms."; mark "showdelay $2";;
    esac
    ;;
reshow)
    # Renderer build 4 (RAM only): switch the output on again at every check after a show.
    case "${2:-}" in
        on) touch /tmp/mu1438-reshow; say 'Re-show on.'; mark "reshow on";;
        off) rm -f /tmp/mu1438-reshow; say 'Re-show off.'; mark "reshow off";;
        *) say "Usage: sh tt.sh reshow on | off   (now: $(test -e /tmp/mu1438-reshow && echo on || echo off))"; exit 2;;
    esac
    ;;
selfwatch)
    # Renderer build 3 follows the screen itself every 25 ms (default). off = supervisor signals
    # again. Read when the renderer starts: replug after changing it.
    case "${2:-}" in
        on) rm -f /tmp/mu1438-no-selfwatch; say 'Self watch on (from the next replug).';;
        off) touch /tmp/mu1438-no-selfwatch; say 'Self watch off (from the next replug).';;
        *) say "Usage: sh tt.sh selfwatch on | off   (now: $(test -e /tmp/mu1438-no-selfwatch && echo off || echo on))"; exit 2;;
    esac
    ;;
guard)
    # Light-speed guard (RAM only): supervisor v9 resumes only after the map has been up N ms.
    case "${2:-}" in
        off|0) rm -f /tmp/mu1462-resume-ms; say 'Guard off.';;
        *[!0-9]*|'') say "Usage: sh tt.sh guard MS | off   (now: $(cat /tmp/mu1462-resume-ms 2>/dev/null || echo off))"; exit 2;;
        *) echo "$2" > /tmp/mu1462-resume-ms; say "Guard: resume after $2 ms of map."; mark "guard $2";;
    esac
    ;;
pause|resume)
    # Handover test by hand (RAM only). 'sh tt.sh manual on' first stops supervisor v8 from
    # sending its own pause/resume. The renderer logs the times in /tmp/mu1438-cluster-runtime.log.
    pid=$(pidin ar 2>/dev/null | grep 'mu1438-cluster/cluster_runtime' | grep -v grep | awk '{print $1}' | head -1)
    test -n "$pid" || { say 'No renderer running.'; exit 1; }
    if test "$1" = pause; then kill -USR1 "$pid"; else kill -USR2 "$pid"; fi
    mark "$1 by hand"
    sleep 1
    tail -2 /tmp/mu1438-cluster-runtime.log 2>/dev/null
    ;;
manual)
    case "${2:-}" in
        on) touch /tmp/mu1462-manual-pause; say 'Manual: supervisor v8 sends no pause/resume. Use sh tt.sh pause / resume.';;
        off) rm -f /tmp/mu1462-manual-pause; say 'Automatic pause/resume again.';;
        *) say "Usage: sh tt.sh manual on | off   (now: $(test -e /tmp/mu1462-manual-pause && echo on || echo off))"; exit 2;;
    esac
    ;;
rec)
    # Crash recorder (RAM only, nothing on flash). Two background loops:
    #  - the system log as it happens -> /tmp/tt-slog-live.txt. The head unit's own CarPlay video
    #    reports there (<SCREEN>, NvssVideoImpl, nvdisp); after a CarPlay crash the system log came
    #    back empty, so 'save' alone never caught it (run50 caught "Window is in use").
    #  - every 2 s what the supervisor, the renderer and dmdt are doing (pidin state, blocked on)
    #    -> /tmp/tt-procwatch.txt, written only when it changes. Shows where the supervisor
    #    hangs when its log goes silent (runs 41, 49, 50).
    # Survives closing SSH, not switching the car off.   on | off | (no word: status)
    LIVE=/tmp/tt-slog-live.txt; PW=/tmp/tt-procwatch.txt; PIDF=/tmp/tt-rec.pid
    running(){ test -f $PIDF && kill -0 "$(head -1 $PIDF)" 2>/dev/null; }
    stop_rec(){
        if test -f $PIDF; then for p in $(cat $PIDF); do kill "$p" 2>/dev/null; done; fi
        slay -f sloginfo > /dev/null 2>&1
        rm -f $PIDF
    }
    case "${2:-}" in
        on)
            # a recorder from an older kit has no process watch: replace it
            stop_rec
            (
                trap '' HUP
                while :; do
                    echo "===== recorder (re)start $(date)" >> $LIVE
                    sloginfo -w >> $LIVE 2>/dev/null
                    # the system log was reset (or sloginfo ended): start again
                    sz=$(wc -c < $LIVE 2>/dev/null); test ${sz:-0} -gt 20000000 && mv -f $LIVE $LIVE.1
                    sleep 1
                done
            ) < /dev/null > /dev/null 2>&1 &
            echo $! > $PIDF
            (
                trap '' HUP
                last=
                while :; do
                    procs=$(pidin ar 2>/dev/null | grep -E 'cluster_supervisor|cluster_runtime|dmdt' | grep -v grep)
                    snap=$(echo "$procs"
                        for p in $(echo "$procs" | awk '{print $1}'); do
                            pidin -p "$p" 2>/dev/null | grep -v 'STATE'
                        done)
                    if test "$snap" != "$last"; then
                        { echo "== $(date)  supervisor log lines: $(wc -l < /tmp/mu1462-supervisor.log 2>/dev/null)"
                          echo "$snap"; } >> $PW
                        last=$snap
                    fi
                    sz=$(wc -c < $PW 2>/dev/null); test ${sz:-0} -gt 20000000 && mv -f $PW $PW.1
                    sleep 2
                done
            ) < /dev/null > /dev/null 2>&1 &
            echo $! >> $PIDF
            say "Recorder on (system log + process watch). It runs until 'sh tt.sh rec off' or the car is switched off."
            ;;
        off)
            stop_rec
            say 'Recorder off.'
            ;;
        *)
            if running; then say "Recorder on: $(wc -c < $LIVE 2>/dev/null || echo 0) bytes system log, $(wc -c < $PW 2>/dev/null || echo 0) bytes process watch"
            else say 'Recorder off.   sh tt.sh rec on | off'; fi
            ;;
    esac
    ;;
layer89)
    # Set the map + banner layer list by hand (memory only; an MMI restart restores it).
    case "${2:-}" in
        stock) dm dc 89 20 16 19 && say 'Context 89 back to stock: 20 16 19';;
        [0-9]*) shift; dm dc 89 "$@" && say "Context 89 is now: $* (tap CAR, then MAP to apply)";;
        *) say "Usage: sh tt.sh layer89 stock | sh tt.sh layer89 20 16   (now: $(ctxlist 89))"; exit 2;;
    esac
    ;;
dm)
    dmdump "${2:-manual}"; say "Display manager state added to /tmp/tt-dmdt.txt (context 89: $(ctxlist 89))"
    ;;
mode)
    case "${2:-}" in
        default) rm -f /tmp/tt-layer-off /tmp/tt-kdk-direct /tmp/tt-rgi-kdk-map /tmp/tt-rgi-noactive /tmp/tt-rgi-kdk /tmp/tt-rgi-nokdk /tmp/tt-rgi-nokdk-small /tmp/tt-rgi-nopad /tmp/tt-rgi-off /tmp/tt-panel-off; say 'Mode: default (our panel inside the stock banner, every view).';;
        direct) touch /tmp/tt-kdk-direct; say 'Mode: experiment, banner window opened directly (no nav-engine view).';;
        layersoff) touch /tmp/tt-layer-off; say 'Mode: stock layer lists (v36 behaviour: banner off on the map). Back: sh tt.sh mode default';;
        kdkmap) touch /tmp/tt-rgi-kdk-map; say 'Mode: stock banner on the map too (v32 behaviour, hides the CarPlay map).';;
        nokdk) touch /tmp/tt-rgi-nokdk; say 'Mode: no stock banner (v26 behaviour: Sport view only).';;
        kdk) rm -f /tmp/tt-rgi-nokdk; touch /tmp/tt-rgi-kdk; say 'Mode: stock banner in every view.';;
        nokdksmall) touch /tmp/tt-rgi-nokdk-small; say 'Mode: no stock banner in the Sport view either.';;
        nopanel) touch /tmp/tt-panel-off; say 'Mode: nopanel (our turn panel off).';;
        noactive) touch /tmp/tt-rgi-noactive; say 'Mode: never report route active.';;
        nopad) touch /tmp/tt-rgi-nopad; say 'Arrows: bare 2-number shape.';;
        off) touch /tmp/tt-rgi-off; say 'Cockpit feed off.';;
        on) rm -f /tmp/tt-rgi-off; say 'Cockpit feed on.';;
        *) say 'Usage: sh tt.sh mode default|layersoff|direct|kdkmap|nokdk|kdk|nopanel|noactive|nopad|off|on'; exit 2;;
    esac
    ;;
panel)
    # Live position/size of our turn panel per view (v50: redrawn at once, no restart).
    #   where      report: skin, view, panel mode and position, the stock banner window
    P=/tmp/tt-panel-pos
    case "${2:-}" in
        kdk-small|kdk-smallmap|kdk-large|left|right|small|flap)
            test -n "${4:-}" || { say 'Usage: sh tt.sh panel kdk-small|kdk-large X Y [SCALE]'; exit 2; }
            touch $P; grep -v "^$2 " $P > $P.new 2>/dev/null; echo "$2 $3 $4 ${5:-1}" >> $P.new; mv $P.new $P
            say "Panel $2 view: x=$3 y=$4 scale=${5:-1} (applies at the next update)";;
        reset) rm -f $P; say 'Panel position back to defaults.';;
        where) touch /tmp/tt-panel-where; sleep 2
               w=$(grep 'where:' /tmp/tt-rgd.log 2>/dev/null | tail -1 | sed 's/^[0-9]* //'); say "$w"
               c=$(echo "$w" | sed -n 's/.* ctx \([0-9-]*\).*/\1/p')
               test -n "$c" && say "context $c layers: $(ctxlist "$c")   (16 HMI, 19 stock map, 20 banner, 60 CarPlay)"
               test -e /tmp/tt-panel-where && say '(no answer: is tt-zoom v50 or newer loaded? sh tt.sh report)';;
        show) cat $P 2>/dev/null || say '(defaults: kdk-small 991 160 1, kdk-large 1105 110 1)';;
        *) say 'Usage: sh tt.sh panel kdk-small|kdk-large X Y [SCALE] | where | reset | show'; exit 2;;
    esac
    ;;
report)
    say "== jar: $(which_jar)    (v63 is the current one)"
    say "== layers: map 10 = $(ctxlist 10)  map+banner 89 = $(ctxlist 89)  (v37 with CarPlay: 16 60 / 20 16 60)"
    grep "layers:" /tmp/tt-rgd.log 2>/dev/null | tail -3
    h=$("$T" "$HOOK" 2>/dev/null); case "$h" in $HOOK_STOCK) say '== CarPlay hook: original';; $HOOK_H320) say '== CarPlay hook: original (h320 preset)';; $FIX_STOCK|$FIX_H320) say '== CarPlay hook: fix1 (old byte patch - replace with: sh tt.sh hookfix)';; $HOOK_TT1) say '== CarPlay hook: TT build 1 (from source: no port leak, always falls back to plain CarPlay)';; $HOOK_TT2) say "== CarPlay hook: TT build 2 (build 1 + look tests: mm $(cat /tmp/tt-hook-mm 2>/dev/null || echo 320 120), safe $(cat /tmp/tt-hook-safe 2>/dev/null || echo 520 110 400 320))";; *) say "== CarPlay hook: unknown ($h)";; esac
    h=$("$T" "$SUP" 2>/dev/null); case "$h" in $SUP_V2) say '== supervisor: v2 (switches the display to 901)';; $SUP_V3) say '== supervisor: v3 (no display switch: no CarPlay map in the map views - replace it)';; $SUP_V4) say '== supervisor: v4 (4 s start delay: slow map - replace with v5)';; $SUP_V5) say "== supervisor: v5 (renderer only on the map view)";; $SUP_V6) say '== supervisor: v6 (test switch version - replace with v7)';; $SUP_V7) say '== supervisor: v7 (renderer runs all session, no display switching)';; $SUP_V8) say '== supervisor: v8 (handover stage 1: pause/resume, no dmdt)';; $SUP_V9) say '== supervisor: v9 (handover stage 1b: 0.1 s polling, test switches)';; *) say "== supervisor: unknown ($h)";; esac
    h=$("$T" "$RT" 2>/dev/null); case "$h" in $RT_STAGE1) say '== renderer: stage 1 (no Sport shift)';; $RT_SPORT) say '== renderer: Sport shift build (killed per tab)';; $RT_TT) say '== renderer: TT build (pause/resume, handover stage 1)';; $RT_TT2) say "== renderer: TT build 2 (pause modes, timestamps; mode $(cat /tmp/mu1438-pause-mode 2>/dev/null || echo close))";; $RT_TT6) say '== renderer: TT build 6 (shows on any car screen, Sport crop every frame)';; $RT_TT5) say '== renderer: TT build 5 (toggle-aware hide/show, self watch)';; $RT_TT4) say "== renderer: TT build 4 (hide, self watch, visibility checks; show delay $(cat /tmp/mu1438-show-delay 2>/dev/null || echo 0) ms)";; $RT_TT3) say "== renderer: TT build 3 (hide by default, watches the screen itself; mode $(cat /tmp/mu1438-pause-mode 2>/dev/null || echo hide))";; *) say "== renderer: unknown ($h)";; esac
    tail -3 /tmp/mu1462-supervisor.log 2>/dev/null
    test -f "$K/panel-bg.a8" && say '== panel background: installed' || say '== panel background: none'
    say '== switches:'
    ls /tmp/tt-rgi-* 2>/dev/null || say '(none)'
    say '== iPhone and cockpit (last 8):'
    grep -E 'rgi on=|hmi|combi|listening|hook (dis)?connected' /tmp/tt-rgd.log 2>/dev/null | tail -8
    say '== playback (last 3):'
    grep -E 'play|panel' /tmp/tt-rgd.log 2>/dev/null | tail -6
    say '== nav log:'
    if test -f /tmp/tt-nav.log; then
        say "$(wc -l < /tmp/tt-nav.log) lines; last 5 non-street:"
        grep -v -E ' street | dtm | nextDest | direction ' /tmp/tt-nav.log | tail -8
        say '== guidance view answers:'
        grep -E 'komo(Result|RgSelResult|Type|Enabled|Visible|Gfx)' /tmp/tt-nav.log | tail -8
    else say 'none'; fi
    ;;
seen)
    # v32 research log: what the iPhone has sent so far (lost at power-off, so run save).
    if test -s /tmp/tt-rgd-cat.txt; then
        say "$(grep -c ' type=' /tmp/tt-rgd-cat.txt) distinct maneuvers, $(grep -c '^[0-9]* lg' /tmp/tt-rgd-cat.txt) lane sets. Maneuver types (count type):"
        sed -n 's/.* type=\([0-9]*\).*/\1/p' /tmp/tt-rgd-cat.txt | sort -n | uniq -c
    else say 'Nothing captured yet: start a route on the iPhone (parked is fine).'; fi
    ;;
tostock)
    # Back to stock before a clean SD-card install test. Without "apply" it only reports (read-only).
    # apply (flash, iPhone unplugged): the original package backup restores dio_manager, the hook,
    # renderer, supervisor and firewall; the stock mm-ipod comes back; tt-zoom.jar goes; our other
    # files (markers, route-guidance hook, this kit and its logs) are MOVED to /mnt/app/root/tt-dev-backup.
    S_DIO=1974dbd95df5c33f81da4d8cf2c77b791e5a6466aa65c7d1912ec9ebd8c140e3
    S_MMIPOD=d5af4a342705bd7ac3498a080e24faf5702c6fc2960895f58aa2800268126464
    DIO=/mnt/app/eso/bin/apps/dio_manager; SB=/mnt/app/armle/usr/sbin; PFD=/mnt/system/etc; A=/mnt/app/root/tt-dev-backup
    ob=
    for d in $(ls -d /mnt/app/root/altscreen-backups/mu1462-* 2>/dev/null); do
        test -z "$ob" && test -f "$d/manifest" && test -f "$d/altscreen-mu1462-install.sh" && grep -q "^dio_manager|.*|$S_DIO|" "$d/manifest" && ob=$d
    done
    say '== now on the car'
    "$T" $S_DIO "$DIO" 2>/dev/null && say 'dio_manager       stock' || say 'dio_manager       CHANGED'
    test -e "$HOOK" && say 'CarPlay hook      present' || say 'CarPlay hook      none'
    test -e "$RT" && say 'renderer          present' || say 'renderer          none'
    test -e "$SUP" && say 'supervisor        present' || say 'supervisor        none'
    pf=$(grep -l 'port = 6031' "$PFD"/pf.conf "$PFD"/pf.*.conf 2>/dev/null); test -n "$pf" && say "firewall rule     in $pf" || say 'firewall rule     none'
    "$T" $S_MMIPOD "$SB/mm-ipod" 2>/dev/null && say 'mm-ipod           stock' || say 'mm-ipod           CHANGED (route-guidance shim)'
    test -e "$JAR" && say 'tt-zoom.jar       present' || say 'tt-zoom.jar       none'
    say "markers           $(ls /mnt/app/root/mibr-carplay111-* /mnt/app/root/tt-rec-off 2>/dev/null | wc -l) file(s)"
    test -n "$ob" && say "original backup   $ob" || say 'original backup   NOT FOUND'
    test -d /mnt/app/root/tt-backups && say 'SD-installer backups present (run the SD restore action instead).'
    if test "${2:-}" != apply; then
        say 'Nothing changed. To go back to stock: sh tt.sh tostock apply   (flash; iPhone unplugged; then restart the MMI)'
        exit 0
    fi
    test -n "$ob" || { say 'No backup with the original dio_manager - stopping, nothing changed.'; exit 1; }
    # list to a file first: in a pipe, grep would find its own 'grep dio_manager' line
    pidin ar > /tmp/tt-ps.txt 2>/dev/null; grep -v grep /tmp/tt-ps.txt | grep -q dio_manager && { say 'A phone is connected (dio_manager running): unplug it, wait 10 s, run again. Seen:'; grep -v grep /tmp/tt-ps.txt | grep dio_manager; exit 1; }
    "$T" $S_MMIPOD "$SB/mm-ipod" 2>/dev/null || "$T" $S_MMIPOD /mnt/app/root/rgd/mm-ipod.orig 2>/dev/null || { say 'No stock mm-ipod copy found - stopping, nothing changed.'; exit 1; }
    say "== 1/4 original package restore ($ob)"
    sh "$ob/altscreen-mu1462-install.sh" --restore --parked || { say 'Package restore failed - stopping. Send me the output.'; exit 1; }
    rw
    mount -uw /mnt/system 2>/dev/null || :
    say '== 2/4 stock mm-ipod'
    if ! "$T" $S_MMIPOD "$SB/mm-ipod" 2>/dev/null; then
        cp /mnt/app/root/rgd/mm-ipod.orig "$SB/mm-ipod.new" && chmod 755 "$SB/mm-ipod.new" && "$T" $S_MMIPOD "$SB/mm-ipod.new" \
            && mv "$SB/mm-ipod.new" "$SB/mm-ipod" || { say 'mm-ipod restore failed'; ro; exit 1; }
    fi
    rm -f "$SB/rgd_real/mm-ipod"; rmdir "$SB/rgd_real" 2>/dev/null || :
    say '== 3/4 HMI jar'
    rm -f "$JAR"
    say '== 4/4 verify'
    bad=0
    "$T" $S_DIO "$DIO" || { say 'dio_manager is not stock'; bad=1; }
    test ! -e "$HOOK" || { say 'hook still present'; bad=1; }
    "$T" $S_MMIPOD "$SB/mm-ipod" || { say 'mm-ipod is not stock'; bad=1; }
    test ! -e "$JAR" || { say 'jar still present'; bad=1; }
    grep -q 'port = 6031' "$PFD"/pf.conf "$PFD"/pf.*.conf 2>/dev/null && { say 'firewall rule still present'; bad=1; }
    if test "$bad" = 1; then mount -ur /mnt/system 2>/dev/null; ro; say 'NOT clean - send me this output. Our files were left in place.'; exit 1; fi
    # our remaining files out of the way (kept, not deleted); the kit itself last
    mkdir -p "$A"
    # NOT lib-target: it is a stock folder (NVIDIA video/media libraries CarPlay needs); our hook
    # in it was already removed by the package restore. Moving it broke stock CarPlay (2026-11-21).
    for f in /mnt/app/root/mibr-carplay111-* /mnt/app/root/tt-rec-off /mnt/app/root/rgd /mnt/app/root/mu1438-cluster; do
        test -e "$f" && mv "$f" "$A/" 2>/dev/null
    done
    mv "$K" "$A/tt"; mv /mnt/app/root/tt.sh "$A/tt.sh" 2>/dev/null
    sync; mount -ur /mnt/system 2>/dev/null; mount -ur /mnt/app 2>/dev/null
    echo 'Stock again (dio_manager, mm-ipod, firewall checked). Our files and your logs are in /mnt/app/root/tt-dev-backup.'
    echo 'Restart the MMI (hold the knob), check CarPlay works as stock, then do the SD card install.'
    exit 0
    ;;
settings)
    # v63: our rows in map options and MMI settings. off = stock menus again (RAM, until restart; reopen the menu)
    case "${2:-}" in
        off) touch /tmp/tt-settings-off; say "Our setting rows off (stock menus from the next MMI restart for map options, next opening for MMI settings).";;
        on) rm -f /tmp/tt-settings-off; say "Our setting rows on (next MMI restart for map options, next opening for MMI settings).";;
        *) grep -e "settings page" -e "card: setting" /tmp/tt-rgd.log | tail -20; say "Usage: sh tt.sh settings on|off   (no argument: the page log)";;
    esac
    ;;
bug)
    # one command after a fault: marker in every log, status report, then save (route recording included)
    shift; mark "BUG ${*:-}"
    sh "$0" report > /tmp/tt-report.txt 2>&1
    sh "$0" save
    ;;
routes)
    for d in $ROUTEDIRS; do test -d "$d" && { say "== $d"; ls -l "$d" | grep route-; }; done
    test -e /tmp/tt-rec-off -o -e /mnt/app/root/tt-rec-off && say "Recorder is OFF (sh tt.sh routerec on)" || say "Recorder is on: one file per route, newest last. Replay one: sh tt.sh play route N"
    ;;
routerec)
    case "${2:-}" in
        on) rm -f /tmp/tt-rec-off; test -e /mnt/app/root/tt-rec-off && { rw; rm -f /mnt/app/root/tt-rec-off; ro; }; say "Route recorder on (from the next route).";;
        off) touch /tmp/tt-rec-off; say "Route recorder off until the next MMI restart.";;
        never) touch /tmp/tt-rec-off; rw; touch /mnt/app/root/tt-rec-off; ro; say "Route recorder off for good (flash marker). Back on: sh tt.sh routerec on";;
        *) say "Usage: sh tt.sh routerec on | off (until restart) | never (for good)";;
    esac
    ;;
save)
    n=1; while test -e "$K/logs/run$n"; do n=$((n+1)); done
    sloginfo 2>/dev/null | tail -4000 > /tmp/tt-slog.txt
    rw; mkdir -p "$K/logs/run$n"
    for f in /tmp/tt-rgd.log /tmp/tt-rgd.log.1 /tmp/tt-nav.log /tmp/tt-zoom.log /tmp/tt-rgd.txt /tmp/tt-rgd-cat.txt /tmp/tt-route.txt /tmp/tt-dmdt.txt /tmp/altscreen111.log /tmp/mu1438-cluster-runtime.log /tmp/mibr-alt111-gen2.status /tmp/mibr-carplay111.state /tmp/tt-diag.txt /tmp/tt-slog.txt /tmp/mu1462-supervisor.log /tmp/tt-slog-marks.txt /tmp/tt-slog-live.txt /tmp/tt-slog-live.txt.1 /tmp/tt-procwatch.txt /tmp/tt-procwatch.txt.1 /tmp/tt-report.txt /tmp/tt-routes/route-*.txt; do test -f "$f" && cp "$f" "$K/logs/run$n/"; done
    ro
    say "Saved to $K/logs/run$n (kept across MMI restarts). On the Mac, this run only:"
    say "scp -O -r -o HostKeyAlgorithms=+ssh-rsa -o PubkeyAcceptedAlgorithms=+ssh-rsa root@10.173.189.1:$K/logs/run$n ~/Downloads/"
    ;;
*)
    say 'sh tt.sh report              what is installed and what it is doing (paste this)'
    say 'sh tt.sh use v63             switch version (v63 ... v12), then restart the MMI'
    say 'sh tt.sh play map|new|fit|win|kdk|panel|komo|stock  parked tests'
    say 'sh tt.sh play arrows | stop  arrow catalogue / stop playback'
    say 'sh tt.sh tostock [apply]     back to stock before a clean SD install (report only without apply)'
    say 'sh tt.sh bug [what you saw]  after a fault: marks the logs, writes a report, saves everything (before any MMI restart)'
    say 'sh tt.sh routes              list the automatic route recordings (SD card or RAM)'
    say 'sh tt.sh play route [N]      replay recording N (newest without N) through the live path'
    say 'sh tt.sh test                the full guided test, Enter for each step, then saves the logs'
    say 'sh tt.sh check               v38 guided check (Enter for each step, c marks a CarPlay crash)'
    say 'sh tt.sh hookfix [undo]      CarPlay hook TT build 2 (flash, then MMI restart)'
    say 'sh tt.sh autozoom on|off|steps N  zoom the map in near turns, out after'
    say 'sh tt.sh banner always|auto|alpha N  our banner always / only near turns, background opacity'
    say 'sh tt.sh tele on|off|demo|rot|boot  OpenHaldex autocross view (demo: fake values, no board)'
    say 'sh tt.sh eta on|off             the phone app'"'"'s own ETA bar on the cockpit map'
    say 'sh tt.sh look 1|2|3|4|reset  how much road the cockpit map shows (next replug, RAM only)'
    say 'sh tt.sh sup [v6|v7|undo]    renderer supervisor: v6 (keeps the display switch), v7, or back to v2 (flash, then replug)'
    say 'sh tt.sh crash               check: tab flips, map return, unplug and replug'
    say 'sh tt.sh card auto|off|on|left|right|top|default  the iPhone card: near turns / never / always, position'
    say 'sh tt.sh unstick             after a crash, when CarPlay will not reconnect: clear the old session'
    say 'sh tt.sh delay SECONDS       renderer start delay on the map view (default 1)'
    say 'sh tt.sh diag                when the CarPlay icon only flickers: record who holds the ports'
    say 'sh tt.sh mark [text]         put a marker line into all logs (e.g. right after a crash)'
    say 'sh tt.sh layers              parked layer test for the map + banner (context 89)'
    say 'sh tt.sh layer89 stock|LIST  set the map + banner layers by hand (memory only)'
    say 'sh tt.sh dm [label]          add the display manager state to the logs'
    say 'sh tt.sh seen                what the iPhone sent so far (maneuver types, lanes)'
    say 'sh tt.sh mode default|nokdk|kdk|nopanel|noactive|nopad|off|on'
    say 'sh tt.sh panel kdk-small|kdk-large X Y [S]  move/resize the panel live (reset | show)'
    say 'sh tt.sh save                keep the logs across MMI restarts'
    say 'sh tt.sh rec on|off          crash recorder: live system log + process watch (RAM)'
    say 'sh tt.sh handover [undo]     renderer pause/resume + supervisor v9 (flash, then replug)'
    say 'sh tt.sh mode close|keepkd|hide   renderer pause mode (RAM)'
    say 'sh tt.sh resumeon map|any    resume on the map view or any car screen (RAM)'
    say 'sh tt.sh guard MS|off        resume only after MS ms of map (RAM)'
    say 'sh tt.sh manual on|off       handover test by hand: sh tt.sh pause / resume (RAM)'
    say 'sh tt.sh remove              take tt-zoom.jar out (restart the MMI)'
    ;;
esac
