package net.krodark.asterion.dev;

/** Use Minecraft's offline developer mode only for the launcher's dummy session. */
public final class OfflineLaunchArguments {
    private OfflineLaunchArguments() { }
    public static String[] prepare(String[] args) {
        String token=null;
        for(int i=0;i<args.length;i++) {
            if(args[i].equals("--offlineDeveloperMode"))return args;
            if(args[i].equals("--accessToken") && i+1<args.length)token=args[++i];
            else if(args[i].startsWith("--accessToken="))token=args[i].substring("--accessToken=".length());
        }
        if(token!=null && !token.isBlank() && !token.equals("FabricMC") && !token.equals("0"))return args;
        String[] prepared=java.util.Arrays.copyOf(args,args.length+1);
        prepared[args.length]="--offlineDeveloperMode";
        return prepared;
    }
}
