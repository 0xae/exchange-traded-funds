package com.cts.etf;

import com.cts.etf.api.EtfApi;
import com.cts.etf.api.SecurityBasketApi;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableMap;
import net.corda.core.messaging.CordaRPCOps;
import net.corda.examples.obligation.ObligationApi;
import net.corda.webserver.services.WebServerPluginRegistry;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

public class EtfPlugin /*implements WebServerPluginRegistry*/ {
   /* private final List<Function<CordaRPCOps, ?>> webApis = ImmutableList.of
            (SecurityBasketApi::new, EtfApi::new, ObligationApi::new);

    private final Map<String, String> staticServeDirs = ImmutableMap.of(
            "obligation", getClass().getClassLoader().getResource("obligationWeb").toExternalForm()
    );

    @Override
    public List<Function<CordaRPCOps, ?>> getWebApis() {
        return webApis;
    }

    @Override
    public Map<String, String> getStaticServeDirs() {
        return staticServeDirs;
    }

    @Override
    public void customizeJSONSerialization(ObjectMapper objectMapper) {
    }*/
}
