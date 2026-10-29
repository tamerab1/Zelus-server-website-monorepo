import org.gradle.internal.os.OperatingSystem

plugins {
	id("reason-kotlin")
	application
}

dependencies {
	implementation(projects.kronosServer)
	implementation(projects.kronosServerKotlinCoupled)

	implementation(projects.logging)
	implementation(projects.loggingSentry)

	implementation(projects.playerAttributes)
	implementation(projects.coreCombatApiReason)

	implementation(projects.interfaceAdvancedsettings)
	implementation(projects.interfaceTabsettings)
	implementation(projects.interfaceIkod)
	implementation(projects.interfaceCharactercreator)
	implementation(projects.coreModuleTest)
	implementation(projects.collectionlog)
	implementation(projects.friendlist)
	implementation(projects.clanchat)
	implementation(projects.playerMusic)
	implementation(projects.playerGroupiron)
	implementation(projects.playerChatFilter)
	implementation(projects.royaltitans)
	implementation(projects.npcNex)
	implementation(projects.dominionOfEchoes)
	implementation(projects.doomOfMokhaiotl)
	implementation(projects.yama)
	implementation(projects.donationdeals)
	implementation(projects.tormenteddemon)
	implementation(projects.gemstoneCrab)
	implementation(projects.worldList)
	implementation(projects.tradepost)
	implementation(projects.playerMongo)
	implementation(projects.economyProtection)

	implementation(libs.hotswap.agent.core)
}

val defaultJvmArgs = listOf(
	"-XX:-OmitStackTraceInFastThrow",
	"-Xms1g",
	// No -Xmx was set here at all, letting the JVM default to ~1/4 of total system RAM with no cap.
	// Real hs_err_pid*.log crash reports found in this repo's root show this exact process
	// (boot.Boot) previously dying with "insufficient memory... native mmap failed... out of
	// physical RAM or swap" -- an unbounded heap fighting the client JVM, gradle daemon, and
	// kotlin daemon for the same machine's RAM is a live risk for exactly that failure, and it
	// crashes the JVM silently (no Java exception, no log line) rather than throwing something
	// catchable. A fixed, safe cap plus a heap dump on OOM turns "process vanished with zero
	// trace" into "here is the actual heap dump" if it ever happens again.
	"-Xmx6g",
	"-XX:+HeapDumpOnOutOfMemoryError",
	"-XX:AutoBoxCacheMax=65535",
	"--enable-preview",
	"--add-opens=java.base/jdk.internal.vm=ALL-UNNAMED",
	"--add-opens=java.base/jdk.internal.loader=ALL-UNNAMED",
	"--add-opens=java.base/java.lang=ALL-UNNAMED",
	"--add-opens=java.base/java.util=ALL-UNNAMED",
	"--enable-native-access=ALL-UNNAMED",
	"-XX:CompileThreshold=1500",
	"-Dslf4j.internal.verbosity=warn",
	"-XX:+UseCodeCacheFlushing",
	"-XX:ReservedCodeCacheSize=128M",
)

application {
	mainClass.set("boot.Boot")
	applicationDefaultJvmArgs += defaultJvmArgs
}

val buildCache = tasks.register<Exec>("build_cache") {
	group = "_reason"

	workingDir = rootDir
	val os = OperatingSystem.current()
	if (os.isWindows) {
		commandLine(".dev/tool-cache-packer.exe")
	} else {
		commandLine(".dev/tool-cache-packer")
	}
	environment("RUST_LOG", "info")
	args("--in", "../data zelus/data/cache/toml/", "--out", "../data zelus/data/cache/")
}

pluginManager.withPlugin("application") {
	tasks.run.configure {
		workingDir = rootProject.projectDir
	}

	tasks.installDist {
		dependsOn(buildCache)
	}
}

val jrebelRoot = rootDir.resolve(".jrebel/lib/")
val jrebelAgentPath: String = when {
	OperatingSystem.current().isWindows -> jrebelRoot.resolve("jrebel64.dll").absolutePath
	OperatingSystem.current().isMacOsX -> jrebelRoot.resolve("libjrebel64.dylib").absolutePath
	OperatingSystem.current().isLinux -> jrebelRoot.resolve("libjrebel64.so").absolutePath
	else -> throw GradleException("Unsupported OS for JRebel agent")
}

// Local `run` only. Prod doesn't use these args: it runs in Docker with KRONOS_BOOT_OPTS / GAME_SERVER_MEM_LIMIT from
// the DOCKER_COMPOSE_ENV secret (docker-compose-prod.yml). Rev 241 loads the whole map at boot (live set 2.16 GB
// right after boot, 2026-10-27) and grew past 6 GB in play, so local runs get 10 GB; prod needs the same before
// rev 241 ships (box: 22 GB, 16 GB available): -Xms10g -Xmx10g with GAME_SERVER_MEM_LIMIT=12g. The later -Xmx wins.
// Set the whole list: jvmArgs("-Xmx10g") alone replaced the application defaults (the --add-opens included) and boot
// died with IllegalAccessError on jdk.internal.vm.Continuation.
tasks.named<JavaExec>("run") {
	jvmArgs = defaultJvmArgs + "-Xmx10g"
}

tasks.register<JavaExec>("run_hotswap") {
	environment["RUST_LOG"] = "info"
	classpath = sourceSets["main"].runtimeClasspath
	workingDir = rootDir

	jvmArgs = defaultJvmArgs + listOf(
		"-agentpath:$jrebelAgentPath",
		"-Xlog:redefine+class*=warning",
	)
	mainClass.set("boot.Boot")
	dependsOn("hotswap_copy")
}
