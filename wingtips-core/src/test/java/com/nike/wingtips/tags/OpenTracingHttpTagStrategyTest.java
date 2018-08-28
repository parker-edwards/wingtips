package com.nike.wingtips.tags;

import com.nike.wingtips.Span;

import com.tngtech.java.junit.dataprovider.DataProvider;
import com.tngtech.java.junit.dataprovider.DataProviderRunner;

import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Matchers.anyObject;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * Tests the functionality of {@link OpenTracingHttpTagStrategy}.
 */
@RunWith(DataProviderRunner.class)
public class OpenTracingHttpTagStrategyTest {

    private OpenTracingHttpTagStrategy<Object, Object> implSpy;
    private Span spanMock;
    private Object requestMock;
    private Object responseMock;
    private Throwable errorMock;
    private HttpTagAndSpanNamingAdapter<Object, Object> adapterMock;

    @Before
    public void beforeMethod() {
        implSpy = spy(new OpenTracingHttpTagStrategy<>());

        spanMock = mock(Span.class);
        requestMock = mock(Object.class);
        responseMock = mock(Object.class);
        errorMock = mock(Throwable.class);
        adapterMock = mock(HttpTagAndSpanNamingAdapter.class);
    }

    @Test
    public void getDefaultInstance_returns_DEFAULT_INSTANCE() {
        // expect
        assertThat(OpenTracingHttpTagStrategy.getDefaultInstance()).isSameAs(OpenTracingHttpTagStrategy.DEFAULT_INSTANCE);
    }

    @Test
    public void doHandleRequestTagging_puts_expected_tags_based_on_adapter_results() {
        // given
        String adapterHttpMethod = "httpmethod-" + UUID.randomUUID().toString();
        String adapterHttpUrl = "url-" + UUID.randomUUID().toString();

        doReturn(adapterHttpMethod).when(adapterMock).getRequestHttpMethod(anyObject());
        doReturn(adapterHttpUrl).when(adapterMock).getRequestUrl(anyObject());

        // when
        implSpy.doHandleRequestTagging(spanMock, requestMock, adapterMock);

        // then
        verify(adapterMock).getRequestHttpMethod(requestMock);
        verify(adapterMock).getRequestUrl(requestMock);

        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownOpenTracingTags.HTTP_METHOD, adapterHttpMethod);
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownOpenTracingTags.HTTP_URL, adapterHttpUrl);
    }

    private enum ErrorTaggingScenario {
        ERROR_IS_NOT_NULL(new RuntimeException("boom"), null, true),
        ERROR_IS_NULL_BUT_ADAPTER_ERROR_TAG_VALUE_IS_NOT_BLANK(null, "foo", true),
        ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_NULL(null, null, false),
        ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_EMPTY(null, "", false),
        ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_WHITESPACE(null, "  \n\r\t  ", false);

        public final Throwable error;
        public final String adapterErrorTagValue;
        public final boolean expectErrorTagPutOnSpan;

        ErrorTaggingScenario(Throwable error, String adapterErrorTagValue, boolean expectErrorTagPutOnSpan) {
            this.error = error;
            this.adapterErrorTagValue = adapterErrorTagValue;
            this.expectErrorTagPutOnSpan = expectErrorTagPutOnSpan;
        }
    }

    @DataProvider(value = {
        "ERROR_IS_NOT_NULL",
        "ERROR_IS_NULL_BUT_ADAPTER_ERROR_TAG_VALUE_IS_NOT_BLANK",
        "ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_NULL",
        "ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_EMPTY",
        "ERROR_IS_NULL_AND_ADAPTER_ERROR_TAG_VALUE_IS_WHITESPACE"
    })
    @Test
    public void doHandleResponseAndErrorTagging_puts_expected_tags_based_on_adapter_results_and_error_existence(
        ErrorTaggingScenario scenario
    ) {
        // given
        Integer adapterHttpStatus = 42;
        doReturn(adapterHttpStatus).when(adapterMock).getResponseHttpStatus(anyObject());

        doReturn(scenario.adapterErrorTagValue).when(adapterMock).getErrorResponseTagValue(anyObject());

        // when
        implSpy.doHandleResponseAndErrorTagging(spanMock, requestMock, responseMock, scenario.error, adapterMock);

        // then
        verify(adapterMock).getResponseHttpStatus(responseMock);
        
        verify(implSpy).putTagIfValueIsNotBlank(spanMock, KnownOpenTracingTags.HTTP_STATUS, adapterHttpStatus);

        if (scenario.error == null) {
            // This call is only made if error is null.
            verify(adapterMock).getErrorResponseTagValue(responseMock);
        }
        else {
            verify(adapterMock, never()).getErrorResponseTagValue(anyObject());
        }

        if (scenario.expectErrorTagPutOnSpan) {
            verify(spanMock).putTag(KnownOpenTracingTags.ERROR, "true");
        }
    }

}
