package ru.transferservis.app.ui.quote;

import androidx.arch.core.executor.ArchTaskExecutor;
import androidx.arch.core.executor.TaskExecutor;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.ArrayDeque;
import java.util.Queue;

import ru.transferservis.app.data.model.TariffType;
import ru.transferservis.app.data.remote.ApiService;
import ru.transferservis.app.data.repository.QuoteRepository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;

public final class QuotePublicationTest {
    private final Queue<Runnable> mainQueue = new ArrayDeque<>();
    private QuoteViewModel model;

    @Before
    public void setUp() {
        ArchTaskExecutor.getInstance().setDelegate(new TaskExecutor() {
            @Override public void executeOnDiskIO(Runnable runnable) { runnable.run(); }
            @Override public void postToMainThread(Runnable runnable) { mainQueue.add(runnable); }
            @Override public boolean isMainThread() { return true; }
        });
        ApiService noNetwork = (ApiService) Proxy.newProxyInstance(
                ApiService.class.getClassLoader(), new Class<?>[]{ApiService.class},
                (proxy, method, arguments) -> {
                    throw new AssertionError("Invalid input must not make an API request");
                });
        model = new QuoteViewModel(new QuoteRepository(noNetwork), mainQueue::add);
    }

    @After
    public void tearDown() {
        model.onCleared();
        ArchTaskExecutor.getInstance().setDelegate(null);
    }

    @Test
    public void resetRejectsValidationResultAlreadyQueuedForPublication() {
        model.calculate("x", "Москва", TariffType.COMFORT);
        assertEquals(QuoteUiState.Status.LOADING, model.getUiState().getValue().getStatus());
        assertFalse(mainQueue.isEmpty());

        model.reset();
        while (!mainQueue.isEmpty()) {
            mainQueue.remove().run();
        }

        assertEquals(QuoteUiState.Status.IDLE, model.getUiState().getValue().getStatus());
        assertNull(model.getUiState().getValue().getErrorCode());
        assertNull(model.getUiState().getValue().getQuote());
    }

    @Test
    public void clearingModelRejectsAResultAlreadyQueuedForPublication() {
        model.calculate("x", "Москва", TariffType.COMFORT);
        assertFalse(mainQueue.isEmpty());

        model.onCleared();
        while (!mainQueue.isEmpty()) {
            mainQueue.remove().run();
        }

        assertEquals(QuoteUiState.Status.LOADING, model.getUiState().getValue().getStatus());
        assertNull(model.getUiState().getValue().getErrorCode());
    }
}
