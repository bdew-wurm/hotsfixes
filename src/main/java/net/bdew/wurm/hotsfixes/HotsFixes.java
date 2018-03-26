package net.bdew.wurm.hotsfixes;

import com.wurmonline.server.Servers;
import javassist.CannotCompileException;
import javassist.ClassPool;
import javassist.CtClass;
import javassist.CtMethod;
import javassist.expr.ExprEditor;
import javassist.expr.FieldAccess;
import javassist.expr.MethodCall;
import org.gotti.wurmunlimited.modloader.classhooks.HookManager;
import org.gotti.wurmunlimited.modloader.interfaces.Configurable;
import org.gotti.wurmunlimited.modloader.interfaces.Initable;
import org.gotti.wurmunlimited.modloader.interfaces.PreInitable;
import org.gotti.wurmunlimited.modloader.interfaces.WurmServerMod;

import java.util.Properties;
import java.util.logging.Level;
import java.util.logging.Logger;

public class HotsFixes implements WurmServerMod, Initable, PreInitable, Configurable {
    private static final Logger logger = Logger.getLogger("HotS Fixes");

    private boolean fixMycelium, removeOreCap, disableWallBashing;
    private boolean allowFungus, allowRiteOfDeath;

    public static void logException(String msg, Throwable e) {
        if (logger != null)
            logger.log(Level.SEVERE, msg, e);
    }

    public static void logWarning(String msg) {
        if (logger != null)
            logger.log(Level.WARNING, msg);
    }

    public static void logInfo(String msg) {
        if (logger != null)
            logger.log(Level.INFO, msg);
    }


    @Override
    public void configure(Properties properties) {
        fixMycelium = Boolean.parseBoolean(properties.getProperty("fixMycelium"));
        removeOreCap = Boolean.parseBoolean(properties.getProperty("removeOreCap"));
        allowFungus = Boolean.parseBoolean(properties.getProperty("allowFungus"));
        allowRiteOfDeath = Boolean.parseBoolean(properties.getProperty("allowRiteOfDeath"));
        disableWallBashing = Boolean.parseBoolean(properties.getProperty("disableWallBashing"));

        logInfo("fixMycelium: " + fixMycelium);
        logInfo("removeOreCap: " + removeOreCap);
        logInfo("allowFungus: " + allowFungus);
        logInfo("allowRiteOfDeath: " + allowRiteOfDeath);
        logInfo("disableWallBashing: " + disableWallBashing);
    }

    @Override
    public void preInit() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            if (allowFungus || allowRiteOfDeath) {
                // Fix BL spells disable in PVE
                ExprEditor spellPveFixer = new ExprEditor() {
                    @Override
                    public void edit(FieldAccess f) throws CannotCompileException {
                        if (f.getFieldName().equals("PVPSERVER")) {
                            f.replace("$_ = true;");
                            logInfo(String.format("Applied PVP server fix in %s.%s:%d",
                                    f.where().getDeclaringClass().getName(),
                                    f.where().getMethodInfo().getName(),
                                    f.getLineNumber()
                            ));
                        }
                    }
                };

                if (allowFungus) {
                    for (CtMethod m : classPool.getCtClass("com.wurmonline.server.spells.Fungus").getDeclaredMethods())
                        m.instrument(spellPveFixer);
                }

                if (allowRiteOfDeath) {
                    for (CtMethod m : classPool.getCtClass("com.wurmonline.server.spells.RiteDeath").getDeclaredMethods())
                        m.instrument(spellPveFixer);
                }
            }
        } catch (Throwable e) {
            throw new RuntimeException("Error modifying spells", e);
        }
    }

    @Override
    public void init() {
        try {
            ClassPool classPool = HookManager.getInstance().getClassPool();

            if (fixMycelium) {
                // Make TilePoller think that this is a PVP server - prevent decay and fix spread of Mycelium
                ExprEditor pvpServerFixer = new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("isThisAPvpServer")) {
                            m.replace("$_ = true;");
                            logInfo(String.format("Applied PVP server fix in %s.%s:%d",
                                    m.where().getDeclaringClass().getName(),
                                    m.where().getMethodInfo().getName(),
                                    m.getLineNumber()
                            ));
                        }
                    }
                };

                CtClass ctTilePoller = classPool.getCtClass("com.wurmonline.server.zones.TilePoller");
                for (CtMethod m : ctTilePoller.getDeclaredMethods()) {
                    m.instrument(pvpServerFixer);
                }
            }

            if (removeOreCap) {
                // Remove ore cap
                CtClass ctTileRockBehaviour = classPool.getCtClass("com.wurmonline.server.behaviours.TileRockBehaviour");
                ctTileRockBehaviour.getConstructor("()V").instrument(new ExprEditor() {
                    @Override
                    public void edit(MethodCall m) throws CannotCompileException {
                        if (m.getMethodName().equals("isThisAHomeServer")) {
                            m.replace("$_ = false;");
                            logInfo(String.format("Applied home server fix in %s.%s:%d",
                                    m.where().getDeclaringClass().getName(),
                                    m.where().getMethodInfo().getName(),
                                    m.getLineNumber()
                            ));
                        }
                    }
                });
            }

            if (disableWallBashing){
                CtClass ctMethodsStructure = classPool.getCtClass("com.wurmonline.server.behaviours.MethodsStructure");
                ctMethodsStructure.getMethod("checkStructureDestruction", "(Lcom/wurmonline/server/creatures/Creature;Lcom/wurmonline/server/structures/Structure;Lcom/wurmonline/server/zones/VolaTile;)Z")
                        .instrument(new ExprEditor() {
                            @Override
                            public void edit(MethodCall m) throws CannotCompileException {
                                if (m.getMethodName().equals("getKingdomTemplateId")) {
                                    m.replace("$_ = com.wurmonline.server.Servers.localServer.PVPSERVER ? $proceed() : 4;");
                                }
                            }
                        });
            }

        } catch (Throwable e) {
            logException("Error loading mod", e);
            throw new RuntimeException(e);
        }
    }
}
