package com.familyfinance.data.db

import com.familyfinance.domain.model.Account
import com.familyfinance.domain.model.AccountGroup
import com.familyfinance.domain.model.BankCard
import com.familyfinance.domain.model.Category
import com.familyfinance.domain.model.Currency
import com.familyfinance.domain.model.Transaction

fun Currency.toEntity() = CurrencyEntity(uid, name, symbol, decimals, isMain, rateToMain, updatedAt, deleted, deviceId)
fun CurrencyEntity.toDomain() = Currency(uid, name, symbol, decimals, isMain, rateToMain, updatedAt, deleted, deviceId)

fun AccountGroup.toEntity() = AccountGroupEntity(uid, name, sortOrder, archived, updatedAt, deleted, deviceId)
fun AccountGroupEntity.toDomain() = AccountGroup(uid, name, sortOrder, archived, updatedAt, deleted, deviceId)

fun Account.toEntity() =
    AccountEntity(uid, groupUid, name, currencyCode, sortOrder, hidden, note, updatedAt, deleted, deviceId)
fun AccountEntity.toDomain() =
    Account(uid, groupUid, name, currencyCode, sortOrder, hidden, note, updatedAt, deleted, deviceId)

fun Category.toEntity() =
    CategoryEntity(uid, parentUid, name, type, sortOrder, archived, updatedAt, deleted, deviceId, icon)
fun CategoryEntity.toDomain() =
    Category(uid, parentUid, name, type, sortOrder, archived, updatedAt, deleted, deviceId, icon)

fun Transaction.toEntity() = TransactionEntity(
    uid, type, date, amount, currencyCode, accountUid, accountAmount, toAccountUid, toAccountAmount,
    categoryUid, note, updatedAt, deleted, deviceId, source,
)
fun TransactionEntity.toDomain() = Transaction(
    uid, type, date, amount, currencyCode, accountUid, accountAmount, toAccountUid, toAccountAmount,
    categoryUid, note, updatedAt, deleted, deviceId, source,
)

fun BankCard.toEntity() = BankCardEntity(uid, bank, last4, accountUid, updatedAt, deleted, deviceId)
fun BankCardEntity.toDomain() = BankCard(uid, bank, last4, accountUid, updatedAt, deleted, deviceId)
