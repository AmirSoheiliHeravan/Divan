package com.divan.app.ui.nav

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddCircle
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.ReceiptLong
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.divan.app.di.ServiceLocator
import com.divan.app.ui.accounts.AccountEditScreen
import com.divan.app.ui.accounts.AccountsScreen
import com.divan.app.ui.assets.AssetsScreen
import com.divan.app.ui.budgets.BudgetsScreen
import com.divan.app.ui.categories.CategoriesScreen
import com.divan.app.ui.checks.ChecksScreen
import com.divan.app.ui.counterparties.CounterpartiesScreen
import com.divan.app.ui.currencies.CurrenciesScreen
import com.divan.app.ui.dashboard.DashboardScreen
import com.divan.app.ui.debts.DebtsScreen
import com.divan.app.ui.family.FamilyScreen
import com.divan.app.ui.loans.LoanDetailScreen
import com.divan.app.ui.loans.LoanEditScreen
import com.divan.app.ui.loans.LoansScreen
import com.divan.app.ui.onboarding.WizardScreen
import com.divan.app.ui.products.InvoiceEditScreen
import com.divan.app.ui.products.InvoicesScreen
import com.divan.app.ui.products.ProductEditScreen
import com.divan.app.ui.products.ProductsScreen
import com.divan.app.ui.recurring.RecurringScreen
import com.divan.app.ui.reports.ReportsScreen
import com.divan.app.ui.settings.SmsRulesScreen
import com.divan.app.ui.settings.SettingsScreen
import com.divan.app.ui.transactions.AddTransactionScreen
import com.divan.app.ui.transactions.TransactionsScreen

object Routes {
    const val WIZARD = "wizard"
    const val DASHBOARD = "dashboard"
    const val TXNS = "txns"
    const val ADD_TXN = "add_txn?type={type}"
    const val REPORTS = "reports"
    const val SETTINGS = "settings"

    const val ACCOUNTS = "accounts"
    const val ACCOUNT_EDIT = "account_edit?id={id}"
    const val CATEGORIES = "categories"
    const val CURRENCIES = "currencies"
    const val LOANS = "loans"
    const val LOAN_EDIT = "loan_edit?id={id}"
    const val LOAN_DETAIL = "loan_detail?id={id}"
    const val DEBTS = "debts"
    const val CHECKS = "checks"
    const val BUDGETS = "budgets"
    const val RECURRING = "recurring"
    const val PRODUCTS = "products"
    const val PRODUCT_EDIT = "product_edit?id={id}"
    const val INVOICES = "invoices"
    const val INVOICE_EDIT = "invoice_edit?kind={kind}"
    const val ASSETS = "assets"
    const val FAMILY = "family"
    const val COUNTERPARTIES = "counterparties"
    const val SMS_RULES = "sms_rules"
    const val BACKUP = "backup"

    fun addTxn(type: String = "expense") = "add_txn?type=$type"
    fun accountEdit(id: String = "") = "account_edit?id=$id"
    fun loanEdit(id: String = "") = "loan_edit?id=$id"
    fun loanDetail(id: String) = "loan_detail?id=$id"
    fun productEdit(id: String = "") = "product_edit?id=$id"
    fun invoiceEdit(kind: String = "PURCHASE") = "invoice_edit?kind=$kind"
}

@Composable
fun AppRoot(quickAddParam: String?) {
    val wizardDone by ServiceLocator.settings.wizardDone.collectAsState(initial = true)
    key(wizardDone) {
        val nav = rememberNavController()
        if (!wizardDone) {
            NavHost(nav, startDestination = Routes.WIZARD) {
                composable(Routes.WIZARD) { WizardScreen(nav) }
            }
        } else {
            MainShell(nav, quickAddParam)
        }
    }
}

private data class Tab(val route: String, val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector)

@Composable
private fun MainShell(nav: NavHostController, quickAddParam: String?) {
    val tabs = listOf(
        Tab(Routes.DASHBOARD, "خانه", Icons.Filled.Home),
        Tab(Routes.TXNS, "تراکنش‌ها", Icons.Filled.ReceiptLong),
        Tab(Routes.REPORTS, "گزارش‌ها", Icons.Filled.PieChart),
        Tab(Routes.SETTINGS, "تنظیمات", Icons.Filled.Settings),
    )
    val backStack by nav.currentBackStackEntryAsState()
    val currentRoute = backStack?.destination?.route

    Scaffold(
        bottomBar = {
            if (currentRoute in tabs.map { it.route }) {
                NavigationBar {
                    tabs.take(2).forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = { nav.navigate(t.route) { popUpTo(Routes.DASHBOARD); launchSingleTop = true } },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                    NavigationBarItem(
                        selected = false,
                        onClick = { nav.navigate(Routes.addTxn(quickAddParam ?: "expense")) { launchSingleTop = true } },
                        icon = { Icon(Icons.Filled.AddCircle, null) },
                        label = { Text("ثبت") },
                    )
                    tabs.drop(2).forEach { t ->
                        NavigationBarItem(
                            selected = currentRoute == t.route,
                            onClick = { nav.navigate(t.route) { popUpTo(Routes.DASHBOARD); launchSingleTop = true } },
                            icon = { Icon(t.icon, null) },
                            label = { Text(t.label) },
                        )
                    }
                }
            }
        }
    ) { pad ->
        NavHost(
            nav,
            startDestination = Routes.DASHBOARD,
            modifier = Modifier.padding(pad),
        ) {
            composable(Routes.DASHBOARD) { DashboardScreen(nav) }
            composable(Routes.TXNS) { TransactionsScreen(nav) }
            composable("add_txn?type={type}") { entry ->
                AddTransactionScreen(nav, entry.arguments?.getString("type") ?: "expense")
            }
            composable(Routes.REPORTS) { ReportsScreen(nav) }
            composable(Routes.SETTINGS) { SettingsScreen(nav) }

            composable(Routes.ACCOUNTS) { AccountsScreen(nav) }
            composable("account_edit?id={id}") { entry ->
                AccountEditScreen(nav, entry.arguments?.getString("id") ?: "")
            }
            composable(Routes.CATEGORIES) { CategoriesScreen(nav) }
            composable(Routes.CURRENCIES) { CurrenciesScreen(nav) }
            composable(Routes.LOANS) { LoansScreen(nav) }
            composable("loan_edit?id={id}") { entry ->
                LoanEditScreen(nav, entry.arguments?.getString("id") ?: "")
            }
            composable("loan_detail?id={id}") { entry ->
                LoanDetailScreen(nav, entry.arguments?.getString("id") ?: "")
            }
            composable(Routes.DEBTS) { DebtsScreen(nav) }
            composable(Routes.CHECKS) { ChecksScreen(nav) }
            composable(Routes.BUDGETS) { BudgetsScreen(nav) }
            composable(Routes.RECURRING) { RecurringScreen(nav) }
            composable(Routes.PRODUCTS) { ProductsScreen(nav) }
            composable("product_edit?id={id}") { entry ->
                ProductEditScreen(nav, entry.arguments?.getString("id") ?: "")
            }
            composable(Routes.INVOICES) { InvoicesScreen(nav) }
            composable("invoice_edit?kind={kind}") { entry ->
                InvoiceEditScreen(nav, entry.arguments?.getString("kind") ?: "PURCHASE")
            }
            composable(Routes.ASSETS) { AssetsScreen(nav) }
            composable(Routes.FAMILY) { FamilyScreen(nav) }
            composable(Routes.COUNTERPARTIES) { CounterpartiesScreen(nav) }
            composable(Routes.SMS_RULES) { SmsRulesScreen(nav) }
            composable(Routes.BACKUP) { com.divan.app.ui.settings.BackupScreen(nav) }
        }
    }
}
