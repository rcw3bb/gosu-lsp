package xyz.ronella.gosu.lsp.analysis;

import org.eclipse.lsp4j.PublishDiagnosticsParams;
import org.eclipse.lsp4j.services.LanguageClient;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class DiagnosticsPublisherTest {

    @Mock
    private LanguageClient client;

    private DiagnosticsPublisher publisher;

    @BeforeEach
    void setUp() {
        publisher = new DiagnosticsPublisher();
    }

    @Test
    void publishDiagnostics_withoutClient_doesNotThrow() {
        // client not connected — should silently no-op
        assertDoesNotThrow(() -> publisher.publishDiagnostics("file:///foo.gs", "class Foo {}"));
    }

    @Test
    void publishDiagnostics_withClient_callsPublishDiagnostics() {
        publisher.connect(client);
        publisher.publishDiagnostics("file:///foo.gs", "class Foo {}");
        verify(client, times(1)).publishDiagnostics(any(PublishDiagnosticsParams.class));
    }

    @Test
    void publishDiagnostics_correctUri() {
        publisher.connect(client);
        ArgumentCaptor<PublishDiagnosticsParams> captor =
                ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
        publisher.publishDiagnostics("file:///test.gs", "package x");
        verify(client).publishDiagnostics(captor.capture());
        assertEquals("file:///test.gs", captor.getValue().getUri());
    }

    @Test
    void publishDiagnostics_validContent_emptyDiagnostics() {
        publisher.connect(client);
        ArgumentCaptor<PublishDiagnosticsParams> captor =
                ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
        publisher.publishDiagnostics("file:///ok.gs", "package com.example\nclass Foo {}");
        verify(client).publishDiagnostics(captor.capture());
        assertTrue(captor.getValue().getDiagnostics().isEmpty(),
                "Valid Gosu source should produce no diagnostics");
    }

    @Test
    void publishDiagnostics_invalidContent_hasDiagnostics() {
        publisher.connect(client);
        ArgumentCaptor<PublishDiagnosticsParams> captor =
                ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
        publisher.publishDiagnostics("file:///bad.gs", "@@@ invalid !!!!");
        verify(client).publishDiagnostics(captor.capture());
        assertFalse(captor.getValue().getDiagnostics().isEmpty(),
                "Invalid source should produce diagnostics");
    }

    @Test
    void publishDiagnostics_diagnosticHasCorrectSource() {
        publisher.connect(client);
        ArgumentCaptor<PublishDiagnosticsParams> captor =
                ArgumentCaptor.forClass(PublishDiagnosticsParams.class);
        publisher.publishDiagnostics("file:///bad.gs", "@@@ invalid");
        verify(client).publishDiagnostics(captor.capture());
        captor.getValue().getDiagnostics().forEach(d ->
                assertEquals("gosu", d.getSource()));
    }

    @Test
    void publishDiagnostics_nullContent_doesNotThrow() {
        publisher.connect(client);
        assertDoesNotThrow(() -> publisher.publishDiagnostics("file:///null.gs", null));
    }
}
