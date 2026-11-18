package org.telegram.messenger;

public final class TelebotsCompanionPolicyTest {
    private static int count;
    private static void check(boolean condition, String message) {
        count++;
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) {
        check(!TelebotsCompanionPolicy.visible(false,true,true,false,false,false,false,false), "off by default");
        check(!TelebotsCompanionPolicy.visible(true,false,true,false,false,false,false,false), "chat toggle off");
        check(TelebotsCompanionPolicy.visible(true,true,true,false,false,false,false,false), "private chat");
        check(!TelebotsCompanionPolicy.visible(true,true,true,true,false,false,false,false), "secret chat");
        check(!TelebotsCompanionPolicy.visible(true,true,false,false,false,false,false,false), "group chat");
        check(!TelebotsCompanionPolicy.visible(true,true,true,false,true,false,false,false), "service chat");
        check(!TelebotsCompanionPolicy.visible(true,true,true,false,false,true,false,false), "user hidden");
        check(!TelebotsCompanionPolicy.visible(true,true,true,false,false,false,true,false), "keyboard visible");
        check(TelebotsCompanionPolicy.animationDurationMs(false,false,false)==220, "normal motion");
        check(TelebotsCompanionPolicy.animationDurationMs(true,false,false)==0, "reduce motion");
        check(TelebotsCompanionPolicy.animationDurationMs(false,true,false)==0, "battery saver");
        check(TelebotsCompanionPolicy.animationDurationMs(false,false,true)==0, "low RAM");
        check(TelebotsCompanionPolicy.clampPosition(-12,300,92)==0, "left bound");
        check(TelebotsCompanionPolicy.clampPosition(999,300,92)==208, "right bound");
        check(TelebotsCompanionPolicy.clampPosition(50,50,92)==0, "small container");
        TelebotsPetState state=TelebotsPetState.initial();
        check(state.energy==80 && state.interactions==0, "pet defaults");
        state=state.interact(TelebotsPetState.PLAY);
        check(state.energy==68 && state.interactions==1, "play changes state");
        state=state.interact(TelebotsPetState.REST);
        check(state.energy==90, "rest restores energy");
        check(TelebotsPetState.cap(120)==100 && TelebotsPetState.cap(-5)==0, "safe bounds");
        System.out.println("PASS Companion policy and pet state: "+count+" assertions");
    }
}
