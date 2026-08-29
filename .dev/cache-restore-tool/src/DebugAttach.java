import com.sun.jdi.*;
import com.sun.jdi.connect.*;
import com.sun.jdi.event.*;
import com.sun.jdi.request.*;

import java.util.*;

// Attaches to a running client JVM (started with
// -agentlib:jdwp=transport=dt_socket,server=y,suspend=n,address=PORT) and sets a breakpoint
// inside pu.ai(int,short) -- the obfuscated client's archive-metadata-load trigger (see
// project_dragdrop_bundle_integration.md for the full trace: id.hl -> jj.ap -> ps.loadData ->
// gp -> bp -> cs -> mr -> pu.ai). Only prints and pauses when the archiveId argument matches one
// of the drag-drop-bundle model ids; every other hit (thousands per second across normal
// gameplay) is silently resumed immediately so this doesn't visibly stall the client.
//
// Usage: DebugAttach <port> <targetArchiveId1,targetArchiveId2,...>
public class DebugAttach {
    public static void main(String[] args) throws Exception {
        int port = Integer.parseInt(args[0]);
        Set<Long> targets = new HashSet<>();
        for (String s : args[1].split(",")) targets.add(Long.parseLong(s.trim()));

        AttachingConnector connector = null;
        for (AttachingConnector c : Bootstrap.virtualMachineManager().attachingConnectors()) {
            if (c.transport().name().equals("dt_socket")) {
                connector = c;
                break;
            }
        }
        if (connector == null) throw new IllegalStateException("no dt_socket attaching connector found");

        Map<String, Connector.Argument> cargs = connector.defaultArguments();
        cargs.get("port").setValue(String.valueOf(port));
        cargs.get("hostname").setValue("127.0.0.1");

        VirtualMachine vm = connector.attach(cargs);
        System.out.println("[debug] attached to VM: " + vm.description());

        List<ReferenceType> classes = vm.classesByName("pu");
        if (classes.isEmpty()) throw new IllegalStateException("class 'pu' not loaded yet -- log in first, then rerun");
        ReferenceType puClass = classes.get(0);

        Method target = null;
        for (Method m : puClass.methodsByName("ai")) {
            List<String> argTypes = m.argumentTypeNames();
            if (argTypes.size() == 2 && argTypes.get(0).equals("int") && argTypes.get(1).equals("short")) {
                target = m;
                break;
            }
        }
        if (target == null) throw new IllegalStateException("pu.ai(int,short) not found -- method signature may differ, listing all 'ai' overloads:");

        Location loc = target.location();
        EventRequestManager erm = vm.eventRequestManager();
        BreakpointRequest bpReq = erm.createBreakpointRequest(loc);
        bpReq.setSuspendPolicy(EventRequest.SUSPEND_EVENT_THREAD);
        bpReq.enable();

        System.out.println("[debug] breakpoint armed on pu.ai(int,short) at " + loc
                + " -- watching for archiveId in " + targets);
        System.out.println("[debug] now log in / equip / open inventory in the client...");

        EventQueue queue = vm.eventQueue();
        long hitCount = 0;
        while (true) {
            EventSet eventSet = queue.remove();
            boolean resumeSet = true;
            for (Event event : eventSet) {
                if (event instanceof BreakpointEvent) {
                    hitCount++;
                    BreakpointEvent be = (BreakpointEvent) event;
                    ThreadReference thread = be.thread();
                    try {
                        StackFrame frame = thread.frame(0);
                        LocalVariable var1LV = null;
                        try {
                            var1LV = frame.visibleVariableByName("var1");
                        } catch (Exception ignored) {
                        }
                        long archiveId = -999;
                        if (var1LV != null) {
                            Value v = frame.getValue(var1LV);
                            if (v instanceof IntegerValue) archiveId = ((IntegerValue) v).value();
                        } else {
                            // fall back to argument values list (index 0 = var1/archiveId)
                            List<Value> argVals = frame.getArgumentValues();
                            if (!argVals.isEmpty() && argVals.get(0) instanceof IntegerValue) {
                                archiveId = ((IntegerValue) argVals.get(0)).value();
                            }
                        }

                        if (targets.contains(archiveId)) {
                            System.out.println("\n[debug] === HIT #" + hitCount + " -- pu.ai called for archiveId=" + archiveId + " ===");
                            ObjectReference thisObj = frame.thisObject();
                            ReferenceType type = thisObj.referenceType();
                            dumpField(thisObj, type, "ah");
                            dumpField(thisObj, type, "af", (int) archiveId);
                            dumpField(thisObj, type, "at");
                            dumpField(thisObj, type, "aw");
                            dumpField(thisObj, type, "bj", (int) archiveId);
                            dumpField(thisObj, type, "bg", (int) archiveId);
                            dumpField(thisObj, type, "bb", (int) archiveId);
                            dumpField(thisObj, type, "bd", (int) archiveId);
                            System.out.println("[debug] resuming after hit #" + hitCount);
                        } else if (hitCount % 500 == 0) {
                            System.out.println("[debug] ...still watching, " + hitCount + " pu.ai() calls seen so far (last archiveId=" + archiveId + ")");
                        }
                    } catch (Exception e) {
                        System.out.println("[debug] error inspecting frame: " + e);
                    }
                }
            }
            if (resumeSet) eventSet.resume();
        }
    }

    static void dumpField(ObjectReference obj, ReferenceType type, String name) {
        dumpField(obj, type, name, -1);
    }

    static void dumpField(ObjectReference obj, ReferenceType type, String name, int index) {
        try {
            Field f = type.fieldByName(name);
            if (f == null) {
                System.out.println("    " + name + " = <no such field>");
                return;
            }
            Value v = obj.getValue(f);
            if (index >= 0 && v instanceof ArrayReference) {
                ArrayReference arr = (ArrayReference) v;
                if (index < arr.length()) {
                    Value elem = arr.getValue(index);
                    System.out.println("    " + name + "[" + index + "] = " + elem + "   (array length=" + arr.length() + ")");
                } else {
                    System.out.println("    " + name + "[" + index + "] = OUT OF BOUNDS (array length=" + arr.length() + ")");
                }
            } else {
                System.out.println("    " + name + " = " + v);
            }
        } catch (Exception e) {
            System.out.println("    " + name + " = <error: " + e + ">");
        }
    }
}
