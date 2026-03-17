package com.apexcal.infrastructure.windows;

import java.io.IOException;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.stream.Collectors;

public final class WindowsStartupIntegration {
    private static final String RUN_KEY = "HKCU\\Software\\Microsoft\\Windows\\CurrentVersion\\Run";
    private static final String VALUE_NAME = "ApexCal";

    public boolean isSupported() {
        return System.getProperty("os.name", "").toLowerCase(Locale.ROOT).contains("win");
    }

    public boolean isEnabled() {
        if (!isSupported()) {
            return false;
        }
        CommandResult result = runCommand(List.of("reg", "query", RUN_KEY, "/v", VALUE_NAME));
        return result.exitCode() == 0;
    }

    public void setEnabled(boolean enabled) {
        if (!isSupported()) {
            throw new IllegalStateException("当前系统不支持 Windows 开机自启");
        }
        boolean currentlyEnabled = isEnabled();
        if (currentlyEnabled == enabled) {
            return;
        }
        if (enabled) {
            enable();
        } else {
            disable();
        }
    }

    private void enable() {
        String launchCommand = resolveLaunchCommand();
        CommandResult result = runCommand(List.of(
                "reg", "add", RUN_KEY,
                "/v", VALUE_NAME,
                "/t", "REG_SZ",
                "/d", launchCommand,
                "/f"));
        if (result.exitCode() != 0) {
            throw new IllegalStateException("写入开机自启失败: " + result.output());
        }
    }

    private void disable() {
        if (!isEnabled()) {
            return;
        }
        CommandResult result = runCommand(List.of("reg", "delete", RUN_KEY, "/v", VALUE_NAME, "/f"));
        if (result.exitCode() != 0) {
            throw new IllegalStateException("移除开机自启失败: " + result.output());
        }
    }

    private String resolveLaunchCommand() {
        String overrideCommand = System.getProperty("apexcal.launch.command", "").trim();
        if (!overrideCommand.isBlank()) {
            return overrideCommand;
        }

        ProcessHandle.Info info = ProcessHandle.current().info();
        String command = info.command().orElseThrow(() -> new IllegalStateException("无法解析当前启动命令"));
        String executableName = Path.of(command).getFileName().toString().toLowerCase(Locale.ROOT);
        if ("java.exe".equals(executableName) || "javaw.exe".equals(executableName)) {
            throw new IllegalStateException("当前开发运行方式无法可靠配置开机自启，请在打包后的 exe 版本中启用");
        }

        List<String> segments = new ArrayList<>();
        segments.add(command);
        segments.addAll(Arrays.asList(info.arguments().orElse(new String[0])));
        return segments.stream().map(this::quote).collect(Collectors.joining(" "));
    }

    private String quote(String value) {
        if (value == null || value.isBlank()) {
            return "\"\"";
        }
        if (value.contains("\"")) {
            value = value.replace("\"", "\\\"");
        }
        if (value.contains(" ") || value.contains("\t")) {
            return '"' + value + '"';
        }
        return value;
    }

    private CommandResult runCommand(List<String> command) {
        try {
            Process process = new ProcessBuilder(command).redirectErrorStream(true).start();
            int exitCode = process.waitFor();
            String output = new String(process.getInputStream().readAllBytes(), StandardCharsets.UTF_8);
            return new CommandResult(exitCode, output.trim());
        } catch (IOException | InterruptedException exception) {
            if (exception instanceof InterruptedException) {
                Thread.currentThread().interrupt();
            }
            throw new IllegalStateException("执行系统命令失败", exception);
        }
    }

    private record CommandResult(int exitCode, String output) {
    }
}