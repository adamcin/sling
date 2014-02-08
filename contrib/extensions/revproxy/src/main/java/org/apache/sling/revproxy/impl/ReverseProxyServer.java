package org.apache.sling.revproxy.impl;

import org.apache.felix.scr.annotations.Activate;
import org.apache.felix.scr.annotations.Component;
import org.apache.felix.scr.annotations.Deactivate;
import org.apache.felix.scr.annotations.Service;
import org.osgi.service.component.ComponentContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Created with IntelliJ IDEA.
 * User: madamcin
 * Date: 2/7/14
 * Time: 6:12 PM
 * To change this template use File | Settings | File Templates.
 */
@Component
@Service(ReverseProxyServer.class)
public class ReverseProxyServer {
    private static final Logger LOGGER = LoggerFactory.getLogger(ReverseProxyServer.class);

    private static final int DEFAULT_PORT = 9080;

    private static final String SYSTEM_PROPERTY_PORT = "org.apache.sling.revproxy.listen.port";

    private final int listenPort;

    public ReverseProxyServer() {
        int listenPort = -1;
        String listenPortProp = System.getProperty(SYSTEM_PROPERTY_PORT);
        if (listenPortProp != null) {
            try {
                listenPort = Integer.valueOf(listenPortProp);
            } catch (NumberFormatException e) {
                LOGGER.error("Invalid ReverseProxyServer port.", e);
            }
        }
        this.listenPort = listenPort <= 0 ? DEFAULT_PORT : listenPort;
    }

    public ReverseProxyServer(int listenPort) {
        this.listenPort = listenPort <= 0 ? DEFAULT_PORT : listenPort;
    }

    @Activate
    protected void activate(ComponentContext ctx) {

    }

    @Deactivate
    protected void deactivate(ComponentContext ctx) {

    }


}
