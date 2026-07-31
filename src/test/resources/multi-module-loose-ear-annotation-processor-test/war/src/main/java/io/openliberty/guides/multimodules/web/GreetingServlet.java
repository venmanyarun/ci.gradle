package io.openliberty.guides.multimodules.web;

import io.openliberty.guides.multimodules.lib.Greeting;
import lombok.extern.java.Log;

import java.io.IOException;
import javax.servlet.ServletException;
import javax.servlet.annotation.WebServlet;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Log
@WebServlet(urlPatterns = "/greeting")
public class GreetingServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    protected void doGet(HttpServletRequest request, HttpServletResponse response)
            throws ServletException, IOException {
        // Uses the Lombok-generated all-args constructor and getter from the JAR module
        Greeting greeting = new Greeting("Hello from Liberty dev mode");
        response.getWriter().append(greeting.getMessage());
    }
}
