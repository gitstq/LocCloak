using System;
using System.Diagnostics;
using System.Drawing;
using System.IO;
using System.IO.Compression;
using System.Net;
using System.Net.Sockets;
using System.Reflection;
using System.Threading;
using System.Windows.Forms;

namespace LocctlLauncher
{
    static class Program
    {
        private static Process nodeProcess = null;
        private static NotifyIcon trayIcon = null;
        private static int serverPort = 8765;
        private static string appDir = "";
        private static Mutex singleInstanceMutex = null;

        [STAThread]
        static void Main()
        {
            Application.EnableVisualStyles();
            Application.SetCompatibleTextRenderingDefault(false);

            // 单实例检测
            bool createdNew;
            singleInstanceMutex = new Mutex(true, "LocCloakLauncherSingleInstance_Global", out createdNew);
            if (!createdNew)
            {
                MessageBox.Show("隐影斗篷程序已经在运行中！\n请查看桌面右下角系统托盘图标。", "隐影斗篷 (LocCloak) 提示", MessageBoxButtons.OK, MessageBoxIcon.Information);
                return;
            }

            // 1. 确定运行根目录（若为单文件嵌入版则自解压）
            try
            {
                appDir = EnsureAppExtracted();
            }
            catch (Exception ex)
            {
                MessageBox.Show("解压运行组件失败: " + ex.Message, "错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // 2. 查找 Node.js 运行时
            string nodeExe = FindNodeExe();
            if (string.IsNullOrEmpty(nodeExe))
            {
                MessageBox.Show("未能找到 Node.js 运行时！\n请确保 bin\\node.exe 存在或系统已安装 Node.js。", "错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // 3. 查找入口脚本
            string cliScript = Path.Combine(appDir, "bin", "locctl.js");
            if (!File.Exists(cliScript))
            {
                MessageBox.Show("未能找到入口脚本: " + cliScript, "错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // 4. 寻找空闲端口
            serverPort = GetFreePort(8765);

            // 5. 启动后台 Node 服务
            try
            {
                StartNodeBackend(nodeExe, cliScript, serverPort);
            }
            catch (Exception ex)
            {
                MessageBox.Show("启动后台服务失败: " + ex.Message, "启动错误", MessageBoxButtons.OK, MessageBoxIcon.Error);
                return;
            }

            // 6. 等待服务就绪并打开前端页面
            ThreadPool.QueueUserWorkItem((state) =>
            {
                if (WaitForServerReady(serverPort, 15))
                {
                    OpenUiWindow(serverPort);
                }
                else
                {
                    MessageBox.Show("本地服务响应超时，请检查杀毒软件是否拦截后台进程。", "提示", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            });

            // 7. 初始化托盘图标
            InitTrayIcon();

            // 注册退出清理
            Application.ApplicationExit += OnAppExit;
            AppDomain.CurrentDomain.ProcessExit += OnAppExit;

            // 运行消息循环
            Application.Run();
        }

        private static string EnsureAppExtracted()
        {
            string baseDir = AppDomain.CurrentDomain.BaseDirectory.TrimEnd('\\', '/');
            string localJs = Path.Combine(baseDir, "bin", "locctl.js");
            if (File.Exists(localJs))
            {
                return baseDir;
            }

            // 检查是否有内嵌资源 bundle.zip
            Assembly asm = Assembly.GetExecutingAssembly();
            Stream resStream = asm.GetManifestResourceStream("bundle.zip");
            if (resStream != null)
            {
                string targetDir = Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), "locctl_app");
                string markerFile = Path.Combine(targetDir, "bin", "locctl.js");

                // 简单的版本升级或首启解压逻辑
                if (!File.Exists(markerFile))
                {
                    Directory.CreateDirectory(targetDir);
                    using (ZipArchive archive = new ZipArchive(resStream))
                    {
                        archive.ExtractToDirectory(targetDir);
                    }
                }
                return targetDir;
            }

            return baseDir;
        }

        private static string FindNodeExe()
        {
            string localNode = Path.Combine(appDir, "bin", "node.exe");
            if (File.Exists(localNode)) return localNode;

            string parentNode = Path.Combine(appDir, "node.exe");
            if (File.Exists(parentNode)) return parentNode;

            string pathEnv = Environment.GetEnvironmentVariable("PATH") ?? "";
            string[] dirs = pathEnv.Split(';');
            foreach (string d in dirs)
            {
                try
                {
                    string candidate = Path.Combine(d.Trim(), "node.exe");
                    if (File.Exists(candidate)) return candidate;
                }
                catch { }
            }
            return null;
        }

        private static int GetFreePort(int startingPort)
        {
            int port = startingPort;
            for (int i = 0; i < 50; i++)
            {
                try
                {
                    TcpListener listener = new TcpListener(IPAddress.Loopback, port);
                    listener.Start();
                    listener.Stop();
                    return port;
                }
                catch
                {
                    port++;
                }
            }
            return startingPort;
        }

        private static void StartNodeBackend(string nodeExe, string cliScript, int port)
        {
            ProcessStartInfo psi = new ProcessStartInfo();
            psi.FileName = nodeExe;
            psi.Arguments = string.Format("\"{0}\" ui --port {1} --no-open", cliScript, port);
            psi.WorkingDirectory = appDir;
            psi.CreateNoWindow = true;
            psi.UseShellExecute = false;

            // 注入内置 adb 路径优先
            string builtinAdb = Path.Combine(appDir, "bin", "platform-tools", "adb.exe");
            if (File.Exists(builtinAdb))
            {
                psi.EnvironmentVariables["LOCCTL_ADB"] = builtinAdb;
            }

            nodeProcess = Process.Start(psi);
        }

        private static bool WaitForServerReady(int port, int maxSeconds)
        {
            string url = string.Format("http://127.0.0.1:{0}/api/status", port);
            DateTime deadline = DateTime.Now.AddSeconds(maxSeconds);
            while (DateTime.Now < deadline)
            {
                try
                {
                    HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                    req.Timeout = 1000;
                    using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse())
                    {
                        if (resp.StatusCode == HttpStatusCode.OK) return true;
                    }
                }
                catch
                {
                    Thread.Sleep(200);
                }
            }
            return false;
        }

        private static void OpenUiWindow(int port)
        {
            string url = string.Format("http://127.0.0.1:{0}/", port);

            string[] edgeCandidates = new string[]
            {
                @"C:\Program Files (x86)\Microsoft\Edge\Application\msedge.exe",
                @"C:\Program Files\Microsoft\Edge\Application\msedge.exe",
                Path.Combine(Environment.GetFolderPath(Environment.SpecialFolder.LocalApplicationData), @"Microsoft\Edge\Application\msedge.exe")
            };

            string edgeExe = null;
            foreach (string p in edgeCandidates)
            {
                if (File.Exists(p))
                {
                    edgeExe = p;
                    break;
                }
            }

            if (!string.IsNullOrEmpty(edgeExe))
            {
                // 以无边框独立桌面应用窗口启动
                ProcessStartInfo psi = new ProcessStartInfo();
                psi.FileName = edgeExe;
                psi.Arguments = string.Format("--app={0} --window-size=1260,860", url);
                Process.Start(psi);
            }
            else
            {
                // 默认浏览器打开
                Process.Start(url);
            }
        }

        private static void InitTrayIcon()
        {
            ContextMenu menu = new ContextMenu();
            menu.MenuItems.Add("打开定位控制台", (s, e) => OpenUiWindow(serverPort));
            menu.MenuItems.Add("还原手机真实定位", (s, e) => ResetLocation(serverPort));
            menu.MenuItems.Add("-");
            menu.MenuItems.Add("退出程序", (s, e) => {
                CleanupAndExit();
            });

            trayIcon = new NotifyIcon();
            trayIcon.Text = "隐影斗篷 (LocCloak) - 双击打开控制台";
            trayIcon.Icon = CreateAppIcon();
            trayIcon.ContextMenu = menu;
            trayIcon.Visible = true;
            trayIcon.DoubleClick += (s, e) => OpenUiWindow(serverPort);

            trayIcon.ShowBalloonTip(2000, "隐影斗篷 (LocCloak)", "程序已启动，内置 ADB 已就绪。\n双击托盘图标可重新打开界面。", ToolTipIcon.Info);
        }

        private static Icon CreateAppIcon()
        {
            // 动态生成一个精致的定位图钉图标
            Bitmap bmp = new Bitmap(32, 32);
            using (Graphics g = Graphics.FromImage(bmp))
            {
                g.SmoothingMode = System.Drawing.Drawing2D.SmoothingMode.AntiAlias;
                g.Clear(Color.Transparent);

                // 外圈蓝色光环
                using (SolidBrush bg = new SolidBrush(Color.FromArgb(18, 21, 27)))
                using (Pen cyanPen = new Pen(Color.FromArgb(0, 229, 255), 2))
                {
                    g.FillEllipse(bg, 1, 1, 30, 30);
                    g.DrawEllipse(cyanPen, 2, 2, 28, 28);
                }

                // 核心定位红点
                using (SolidBrush red = new SolidBrush(Color.FromArgb(255, 82, 82)))
                {
                    g.FillEllipse(red, 10, 8, 12, 12);
                }
                using (SolidBrush dot = new SolidBrush(Color.White))
                {
                    g.FillEllipse(dot, 14, 12, 4, 4);
                }
            }
            return Icon.FromHandle(bmp.GetHicon());
        }

        private static void ResetLocation(int port)
        {
            ThreadPool.QueueUserWorkItem((state) =>
            {
                try
                {
                    string url = string.Format("http://127.0.0.1:{0}/api/inject/stop", port);
                    HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                    req.Method = "POST";
                    req.ContentType = "application/json";
                    req.ContentLength = 0;
                    req.Timeout = 4000;
                    using (HttpWebResponse resp = (HttpWebResponse)req.GetResponse()) { }
                    MessageBox.Show("已成功恢复手机真实定位！", "还原成功", MessageBoxButtons.OK, MessageBoxIcon.Information);
                }
                catch (Exception ex)
                {
                    MessageBox.Show("恢复失败: " + ex.Message, "提示", MessageBoxButtons.OK, MessageBoxIcon.Warning);
                }
            });
        }

        private static void CleanupAndExit()
        {
            if (trayIcon != null)
            {
                trayIcon.Visible = false;
                trayIcon.Dispose();
                trayIcon = null;
            }

            // 退出前自动请求恢复真实定位
            try
            {
                string url = string.Format("http://127.0.0.1:{0}/api/inject/stop", serverPort);
                HttpWebRequest req = (HttpWebRequest)WebRequest.Create(url);
                req.Method = "POST";
                req.ContentType = "application/json";
                req.ContentLength = 0;
                req.Timeout = 1500;
                using (req.GetResponse()) { }
            }
            catch { }

            if (nodeProcess != null && !nodeProcess.HasExited)
            {
                try
                {
                    nodeProcess.Kill();
                }
                catch { }
            }

            if (singleInstanceMutex != null)
            {
                singleInstanceMutex.ReleaseMutex();
                singleInstanceMutex = null;
            }

            Environment.Exit(0);
        }

        private static void OnAppExit(object sender, EventArgs e)
        {
            CleanupAndExit();
        }
    }
}
