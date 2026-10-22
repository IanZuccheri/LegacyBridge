import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.builder.LiteralArgumentBuilder;
import com.mojang.brigadier.builder.RequiredArgumentBuilder;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;

/** Owned API-only probe. No Minecraft, Forge, original or converted mod classes on its classpath. */
public final class BrigadierSuiteProbe {
    private static int effects;
    private static CommandDispatcher<Integer> dispatcher() {
        CommandDispatcher<Integer> d=new CommandDispatcher<>();
        d.register(LiteralArgumentBuilder.<Integer>literal("bridgesuite").requires(level->level>=2)
            .then(LiteralArgumentBuilder.<Integer>literal("scale")
                .then(RequiredArgumentBuilder.<Integer,Integer>argument("value",IntegerArgumentType.integer(0,100))
                    .executes(ctx->{effects++;return 3*IntegerArgumentType.getInteger(ctx,"value");})))
            .then(LiteralArgumentBuilder.<Integer>literal("flag")
                .then(RequiredArgumentBuilder.<Integer,Boolean>argument("enabled",BoolArgumentType.bool())
                    .executes(ctx->{effects++;return BoolArgumentType.getBool(ctx,"enabled")?1:0;}))));
        return d;
    }
    private static void check(boolean value){if(!value)throw new AssertionError("API contract failed");}
    private static void rejected(CommandDispatcher<Integer> d,String text,int permission)throws Exception{
        int before=effects;
        try{d.execute(text,permission);throw new AssertionError("Accepted invalid/unauthorized command");}
        catch(CommandSyntaxException expected){check(effects==before);}
    }
    public static void main(String[] ignored)throws Exception{
        int accepted=0,rejected=0;
        CommandDispatcher<Integer> first=dispatcher();
        for(int generation=0;generation<2;generation++){
            CommandDispatcher<Integer> d=generation==0?first:dispatcher();
            check(d.getRoot().getChildren().size()==1);
            check(d.getRoot().getChild("bridgesuite").getChildren().size()==2);
            check(d.execute("bridgesuite scale 0",2)==0);accepted++;
            check(d.execute("bridgesuite scale 100",4)==300);accepted++;
            check(d.execute("bridgesuite flag true",2)==1);accepted++;
            check(d.execute("bridgesuite flag false",4)==0);accepted++;
            for(String command:new String[]{"bridgesuite scale -1","bridgesuite scale 101","bridgesuite scale one",
                    "bridgesuite flag invalid","bridgesuite flag","bridgesuite unknown","bridgesuite scale 1 extra"}){
                rejected(d,command,4);rejected++;
            }
            rejected(d,"bridgesuite scale 1",1);rejected++;
            rejected(d,"bridgesuite flag true",0);rejected++;
        }
        check(effects==8);check(first.getRoot().getChildren().size()==1);
        System.out.println("API_ONLY accepted="+accepted+" rejected="+rejected+" dispatcherGenerations=2 effects="+effects);
    }
}
