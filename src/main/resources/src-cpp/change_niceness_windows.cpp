using System;
using System.Diagnostics;

namespace ConsoleApp1
{
    internal class Program
    {
        static void Main(string[] args)
        {
            //Parse PID
            int pid = Int32.Parse(args[0].Trim());

            //Parse Priority
            String priority = args[1].ToUpper().Trim();
            int sleep = 1000;
            if(args.Length > 2)
                sleep = Int32.Parse(args[2].Trim());

            ProcessPriorityClass priorityClazz = ProcessPriorityClass.Normal;
            if (priority.Equals("HIGH"))
                priorityClazz = ProcessPriorityClass.High;
            else if (priority.Equals("NORMAL"))
                priorityClazz = ProcessPriorityClass.Normal;
            else if (priority.Equals("ABOVE_NORMAL") || priority.Equals("ABOVENORMAL"))
                priorityClazz = ProcessPriorityClass.AboveNormal;
            else if (priority.Equals("REALTIME"))
                priorityClazz = ProcessPriorityClass.RealTime;

            //Set Process Priorty Class
            using (Process p = Process.GetProcessById(pid))
                p.PriorityClass = priorityClazz;
            Console.WriteLine("Set Process Priority: " + priorityClazz + " of pid:" + pid);
            System.Threading.Thread.Sleep(sleep);
        }
    }
}
