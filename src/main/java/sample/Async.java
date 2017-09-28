/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements. See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership. The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied. See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

/*
 * This is an example of an async servlet that uses an executor to run
 * a slow task and a scheduledexecutor to periodically check the status
 * 
 */
package sample;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.concurrent.Future;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

import javax.annotation.Resource;
import javax.enterprise.concurrent.ManagedScheduledExecutorService;

import javax.servlet.AsyncContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;


@WebServlet(urlPatterns = "/Async", asyncSupported = true)
public class Async extends HttpServlet {
    private static final long serialVersionUID = 1L;
    
    @Resource
    ManagedScheduledExecutorService scheduler;
    @Resource
    ScheduledExecutorService executor;

    public Async() {
    }
    
    // our userdata
    class Baton {
        AsyncContext ctx;
        Future<?> workerFuture;
        ManagedScheduledExecutorService scheduler;
        Future<?> checkerFuture;
    }
    

    protected void print(Baton baton, String msg) { 
        print(baton.ctx, msg);
    }
    
    protected void print(AsyncContext ctx, String msg) { 
        PrintWriter out;
        try {
            out = ctx.getResponse().getWriter();
            synchronized(ctx) { 
                out.print(msg);
                out.flush();
            }
        } catch (IOException e) {
            // XXX: examples are lenient!
        }
    }
    
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        
        final AsyncContext ctx = request.startAsync();

        print(ctx, "servlet initial thread is running\n");

        // Kick off our slow work on another thread using the injected executor
        Future<?>  result  = this.executor.submit(new DoWork(ctx));
        Baton baton = new Baton();
        baton.ctx = ctx;
        baton.scheduler = this.scheduler;
        baton.workerFuture = result;

        // Start pulsing our checker thread
        baton.checkerFuture = this.scheduler.scheduleAtFixedRate(new Checker(baton), 1000, 1000, TimeUnit.MILLISECONDS);
        
        print(baton, "\n\nservlet initial thread is done\n");
    }
    
    // periodically run via the scheduler to check on the response and keep the connection busy
    class Checker implements Runnable {
        Baton baton;
        PrintWriter out;
        int limit = 20;

        public Checker(Baton b) throws IOException {
            baton = b;
        }

        @Override
        public void run() {
            if (baton.workerFuture.isDone() || baton.workerFuture.isCancelled()) { 
                print(baton, "\n response complete");
                baton.ctx.complete();
                // Stop calling us back
                baton.checkerFuture.cancel(true);
            }
            else if (limit-- <= 0) { 
                print(baton, "Time up");
                // Kill our worker runnable
                baton.workerFuture.cancel(true);
                // Stop calling us back
                baton.checkerFuture.cancel(true);
            }
            else { 
                print(baton, "#");
            }
        }
    }
    
    // Pretends to do some slow blocking thing
    class DoWork implements Runnable {
        AsyncContext ctx;
        PrintWriter out;

        DoWork(AsyncContext ctx) throws IOException {
            this.ctx = ctx;
            out = ctx.getResponse().getWriter();
        }

        @Override
        public void run() {
            print(ctx, "callable is running\n");
            try {
                // Pretend to do something slow
                Thread.sleep(10 * 1000);
            } catch (InterruptedException e) {
                // TODO Auto-generated catch block
                e.printStackTrace();
            }
            print(ctx, "callable is DONE\n");
        }
    }
}
