import { defaultShouldDehydrateQuery, type Query } from '@tanstack/react-query';

export const shouldPersistQuery = (query: Query) => query.meta?.persist !== false && defaultShouldDehydrateQuery(query);
