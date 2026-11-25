package com.provguard.desktop;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

interface CaseStore {
    List<ReviewCase> list() throws ReviewException;
    Optional<ReviewCase> find(UUID id) throws ReviewException;
    void insert(ReviewCase item) throws ReviewException;
    void update(ReviewCase item) throws ReviewException;
    void delete(UUID id) throws ReviewException;
    int importCases(List<ReviewCase> items) throws ReviewException;
}
