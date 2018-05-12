package atm.bloodworkxgaming.serverstarter;

import atm.bloodworkxgaming.serverstarter.config.ConfigFile;
import atm.bloodworkxgaming.serverstarter.config.LockFile;
import atm.bloodworkxgaming.serverstarter.packtype.IPackType;
import atm.bloodworkxgaming.serverstarter.packtype.TypeFactory;
import org.apache.commons.io.FileUtils;
import org.yaml.snakeyaml.DumperOptions;
import org.yaml.snakeyaml.Yaml;
import org.yaml.snakeyaml.constructor.Constructor;
import org.yaml.snakeyaml.nodes.Tag;
import org.yaml.snakeyaml.representer.Representer;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.net.URL;
import java.util.*;


public class ServerStarter {
    private static Representer rep;
    private static DumperOptions options;
    public static LockFile lockFile = null;

    static {
        rep = new Representer();
        options = new DumperOptions();
        rep.addClassTag(ConfigFile.class, Tag.MAP);
        rep.addClassTag(LockFile.class, Tag.MAP);
        options.setDefaultFlowStyle(DumperOptions.FlowStyle.AUTO);
    }

    public static void main(String[] args) {
        ConfigFile config = readConfig();
        lockFile = readLockFile();

        if (config == null || lockFile == null) {
            System.err.println("[Error] One file is null: config: " + config + " lock: " + lockFile);
            return;
        }

        // normalize the file so it can be used
        if (config.install.baseInstallPath == null) config.install.baseInstallPath = "";


        if (checkShouldInstall(config)) {
            IPackType packtype = TypeFactory.createPackType(config.install.modpackFormat, config);
            if (packtype == null) {
                System.out.println("Unknown pack format given in config");
                return;
            }

            // packtype.installPack();
            lockFile.packInstalled = true;
            lockFile.packUrl = config.install.modpackUrl;
            saveLockFile(lockFile);

            String forgeVersion = packtype.getForgeVersion();
            String mcVersion = packtype.getMCVersion();
            installForge(config.install.baseInstallPath, forgeVersion, mcVersion);
            lockFile.forgeInstalled = true;
            lockFile.forgeVersion = forgeVersion;
            lockFile.mcVersion = mcVersion;
            saveLockFile(lockFile);
        }
    }

    public static ConfigFile readConfig() {
        Yaml yaml = new Yaml(new Constructor(ConfigFile.class), rep, options);
        try {
            return yaml.load(new FileInputStream(new File("server-setup-config.yaml")));
        } catch (FileNotFoundException e) {
            e.printStackTrace();
            return null;
        }
    }

    public static LockFile readLockFile() {
        Yaml yaml = new Yaml(new Constructor(LockFile.class), rep, options);
        File file = new File("serverstarter.lock");

        if (file.exists()) {
            try {
                return yaml.load(new FileInputStream(file));
            } catch (FileNotFoundException e) {
                return new LockFile();
            }
        } else {
            return new LockFile();
        }
    }

    public static void saveLockFile(LockFile lockFile) {
        Yaml yaml = new Yaml(new Constructor(LockFile.class), rep, options);
        File file = new File("serverstarter.lock");
        ServerStarter.lockFile = lockFile;

        String lock = "#Auto genereated file, DO NOT EDIT!\n" + yaml.dump(lockFile);
        try {
            FileUtils.write(file, lock, "utf-8", false);
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    private static void installForge(String basePath, String forgeVersion, String mcVersion) {
        String temp = mcVersion + "-" + forgeVersion;
        String url = "http://files.minecraftforge.net/maven/net/minecraftforge/forge/" + temp + "/forge-" + temp + "-installer.jar";
        // http://files.minecraftforge.net/maven/net/minecraftforge/forge/1.12.2-14.23.3.2682/forge-1.12.2-14.23.3.2682-installer.jar
        File installerPath = new File(basePath + "forge-" + temp + "-installer.jar");


        try {
            FileUtils.copyURLToFile(new URL(url), installerPath);

            System.out.println("Starting installation of Forge, installer output incoming");
            Process installer = new ProcessBuilder("java", "-jar", installerPath.getAbsolutePath(), "--installServer")
                    .inheritIO()
                    .directory(new File(basePath))
                    .start();

            installer.waitFor();

            System.out.println("Done installing forge, deleting installer!");
            installerPath.delete();
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }

    }

    private static void startServer(ConfigFile configFile) {

        try {
            File forgeUniversal = new File(configFile.install.baseInstallPath
                    + "forge-" + lockFile.mcVersion + "-" + lockFile.forgeVersion + "-universal.jar");

            List<String> arguments = new ArrayList<>();
            Collections.addAll(arguments, "java", "-jar", forgeUniversal.getAbsolutePath());
            arguments.addAll(configFile.launch.javaArgs);

            System.out.println("Starting installation of Forge, installer output incoming");
            Process installer = new ProcessBuilder()
                    .inheritIO()
                    .directory(new File(configFile.install.baseInstallPath))
                    .start();

            installer.waitFor();

        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }

    private static boolean checkShouldInstall(ConfigFile configFile) {
        return !lockFile.forgeInstalled
                || !lockFile.packInstalled
                || !Objects.equals(lockFile.forgeVersion, configFile.install.forgeVersion)
                || !Objects.equals(lockFile.packUrl, configFile.install.modpackUrl);
    }
}
