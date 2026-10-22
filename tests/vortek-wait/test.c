#define _POSIX_C_SOURCE 200809L
#include <assert.h>
#include <pthread.h>
#include <stdbool.h>
#include <stdint.h>
#include <stdio.h>
#include <stdlib.h>
#include <string.h>
#include <time.h>

#define VK_ERROR_DEVICE_LOST (-4)

typedef struct {
    void* elements[8];
    int size;
} ArrayList;

typedef struct {
    uint16_t requestId;
    void* data;
    int size;
} ExtraDataRequest;

typedef struct {
    int clientFd;
    int status;
    ArrayList extraDataRequests;
    pthread_mutex_t extraDataRequestsMutex;
    pthread_cond_t extraDataRequestsCond;
} VkContext;

static void ArrayList_add(ArrayList* list, void* value) {
    assert(list->size < 8);
    list->elements[list->size++] = value;
}

static void ArrayList_removeAt(ArrayList* list, int index) {
    memmove(&list->elements[index], &list->elements[index + 1],
            (list->size - index - 1) * sizeof(void*));
    list->size--;
}

static int sock_read(int fd, void* data, int size) {
    (void)fd;
    memset(data, 0x5a, size);
    return size;
}

/* PRODUCTION_FUNCTIONS */

typedef struct {
    VkContext* context;
    uint16_t requested;
    ExtraDataRequest* result;
    bool finished;
} Waiter;

static void* wait_thread(void* value) {
    Waiter* waiter = value;
    waiter->result = waitForExtraDataRequest(waiter->context, waiter->requested);
    pthread_mutex_lock(&waiter->context->extraDataRequestsMutex);
    waiter->finished = true;
    pthread_mutex_unlock(&waiter->context->extraDataRequestsMutex);
    return NULL;
}

static void pause_briefly(void) {
    struct timespec delay = {.tv_sec = 0, .tv_nsec = 20000000};
    nanosleep(&delay, NULL);
}

int main(void) {
    VkContext context = {0};
    assert(pthread_mutex_init(&context.extraDataRequestsMutex, NULL) == 0);
    assert(pthread_cond_init(&context.extraDataRequestsCond, NULL) == 0);

    Waiter waiter = {.context = &context, .requested = 7};
    pthread_t thread;
    assert(pthread_create(&thread, NULL, wait_thread, &waiter) == 0);
    pause_briefly();
    assert(handleExtraDataRequest(&context, 9, 4));
    pause_briefly();
    pthread_mutex_lock(&context.extraDataRequestsMutex);
    assert(!waiter.finished);
    pthread_mutex_unlock(&context.extraDataRequestsMutex);
    assert(handleExtraDataRequest(&context, 7, 4));
    assert(pthread_join(thread, NULL) == 0);
    assert(waiter.result && waiter.result->requestId == 7);
    assert(((unsigned char*)waiter.result->data)[0] == 0x5a);
    assert(context.extraDataRequests.size == 1);
    assert(((ExtraDataRequest*)context.extraDataRequests.elements[0])->requestId == 9);
    free(waiter.result->data);
    free(waiter.result);
    ExtraDataRequest* extra = context.extraDataRequests.elements[0];
    free(extra->data);
    free(extra);
    context.extraDataRequests.size = 0;

    Waiter cancelled = {.context = &context, .requested = 10};
    assert(pthread_create(&thread, NULL, wait_thread, &cancelled) == 0);
    pause_briefly();
    pthread_mutex_lock(&context.extraDataRequestsMutex);
    context.status = VK_ERROR_DEVICE_LOST;
    pthread_cond_broadcast(&context.extraDataRequestsCond);
    pthread_mutex_unlock(&context.extraDataRequestsMutex);
    assert(pthread_join(thread, NULL) == 0);
    assert(cancelled.finished && cancelled.result == NULL);

    pthread_cond_destroy(&context.extraDataRequestsCond);
    pthread_mutex_destroy(&context.extraDataRequestsMutex);
    puts("PASS: Vortek wait handles matching data, unmatched data and cancellation");
}
