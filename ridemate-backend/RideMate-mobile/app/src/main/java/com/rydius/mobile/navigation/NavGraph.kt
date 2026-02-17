package com.rydius.mobile.navigation

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.rydius.mobile.ui.auth.LoginScreen
import com.rydius.mobile.ui.auth.SignupScreen
import com.rydius.mobile.ui.driver.DriverConfirmationScreen
import com.rydius.mobile.ui.home.HomeScreen
import com.rydius.mobile.ui.passenger.PassengerConfirmationScreen
import com.rydius.mobile.ui.profile.EditProfileScreen
import com.rydius.mobile.ui.profile.ProfileScreen
import com.rydius.mobile.ui.rides.MyRidesScreen

/** All navigation route names. */
object Routes {
    const val SPLASH   = "splash"
    const val LOGIN    = "login"
    const val SIGNUP   = "signup"
    const val HOME     = "home"
    const val DRIVER   = "driver/{startLocation}/{endLocation}/{startLat}/{startLng}/{endLat}/{endLng}/{seats}/{departureTime}"
    const val PASSENGER = "passenger/{startLocation}/{endLocation}/{startLat}/{startLng}/{endLat}/{endLng}/{seats}/{departureTime}"
    const val MY_RIDES = "my_rides"
    const val PROFILE  = "profile"
    const val EDIT_PROFILE = "edit_profile"

    fun driverRoute(
        startLocation: String, endLocation: String,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        seats: Int, departureTime: String
    ) = "driver/${Uri.encode(startLocation)}/${Uri.encode(endLocation)}/$startLat/$startLng/$endLat/$endLng/$seats/${Uri.encode(departureTime)}"

    fun passengerRoute(
        startLocation: String, endLocation: String,
        startLat: Double, startLng: Double,
        endLat: Double, endLng: Double,
        seats: Int, departureTime: String
    ) = "passenger/${Uri.encode(startLocation)}/${Uri.encode(endLocation)}/$startLat/$startLng/$endLat/$endLng/$seats/${Uri.encode(departureTime)}"
}

@Composable
fun AppNavGraph(
    navController: NavHostController,
    isLoggedIn: Boolean
) {
    NavHost(
        navController = navController,
        startDestination = if (isLoggedIn) Routes.HOME else Routes.LOGIN
    ) {
        composable(Routes.LOGIN) {
            LoginScreen(
                onLoginSuccess = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.LOGIN) { inclusive = true }
                    }
                },
                onNavigateToSignup = {
                    navController.navigate(Routes.SIGNUP)
                }
            )
        }

        composable(Routes.SIGNUP) {
            SignupScreen(
                onSignupComplete = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(Routes.SIGNUP) { inclusive = true }
                    }
                },
                onNavigateToLogin = { navController.popBackStack() }
            )
        }

        composable(Routes.HOME) {
            HomeScreen(
                onNavigateToDriver = { start, end, sLat, sLng, eLat, eLng, seats, time ->
                    val route = Routes.driverRoute(
                        start, end, sLat, sLng, eLat, eLng, seats, time
                    )
                    navController.navigate(route)
                },
                onNavigateToPassenger = { start, end, sLat, sLng, eLat, eLng, seats, time ->
                    val route = Routes.passengerRoute(
                        start, end, sLat, sLng, eLat, eLng, seats, time
                    )
                    navController.navigate(route)
                },
                onNavigateToMyRides = { navController.navigate(Routes.MY_RIDES) },
                onNavigateToProfile = { navController.navigate(Routes.PROFILE) }
            )
        }

        composable(
            route = Routes.DRIVER,
            arguments = listOf(
                navArgument("startLocation") { type = NavType.StringType },
                navArgument("endLocation") { type = NavType.StringType },
                navArgument("startLat") { type = NavType.StringType },
                navArgument("startLng") { type = NavType.StringType },
                navArgument("endLat") { type = NavType.StringType },
                navArgument("endLng") { type = NavType.StringType },
                navArgument("seats") { type = NavType.IntType },
                navArgument("departureTime") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            DriverConfirmationScreen(
                startLocation = args.getString("startLocation", ""),
                endLocation = args.getString("endLocation", ""),
                startLat = args.getString("startLat", "0")!!.toDoubleOrNull() ?: 0.0,
                startLng = args.getString("startLng", "0")!!.toDoubleOrNull() ?: 0.0,
                endLat = args.getString("endLat", "0")!!.toDoubleOrNull() ?: 0.0,
                endLng = args.getString("endLng", "0")!!.toDoubleOrNull() ?: 0.0,
                seats = args.getInt("seats", 1),
                departureTime = args.getString("departureTime", ""),
                onBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(
            route = Routes.PASSENGER,
            arguments = listOf(
                navArgument("startLocation") { type = NavType.StringType },
                navArgument("endLocation") { type = NavType.StringType },
                navArgument("startLat") { type = NavType.StringType },
                navArgument("startLng") { type = NavType.StringType },
                navArgument("endLat") { type = NavType.StringType },
                navArgument("endLng") { type = NavType.StringType },
                navArgument("seats") { type = NavType.IntType },
                navArgument("departureTime") { type = NavType.StringType }
            )
        ) { backStackEntry ->
            val args = backStackEntry.arguments!!
            PassengerConfirmationScreen(
                startLocation = args.getString("startLocation", ""),
                endLocation = args.getString("endLocation", ""),
                startLat = args.getString("startLat", "0")!!.toDoubleOrNull() ?: 0.0,
                startLng = args.getString("startLng", "0")!!.toDoubleOrNull() ?: 0.0,
                endLat = args.getString("endLat", "0")!!.toDoubleOrNull() ?: 0.0,
                endLng = args.getString("endLng", "0")!!.toDoubleOrNull() ?: 0.0,
                seats = args.getInt("seats", 1),
                departureTime = args.getString("departureTime", ""),
                onBack = { navController.popBackStack() },
                onNavigateHome = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.MY_RIDES) {
            MyRidesScreen(
                onBack = { navController.popBackStack() },
                onNewRide = {
                    navController.navigate(Routes.HOME) {
                        popUpTo(Routes.HOME) { inclusive = true }
                    }
                }
            )
        }

        composable(Routes.PROFILE) {
            ProfileScreen(
                onBack = { navController.popBackStack() },
                onLogout = {
                    navController.navigate(Routes.LOGIN) {
                        popUpTo(0) { inclusive = true }
                    }
                },
                onNavigateToEditProfile = {
                    navController.navigate(Routes.EDIT_PROFILE)
                }
            )
        }

        composable(Routes.EDIT_PROFILE) {
            EditProfileScreen(
                onBack = { navController.popBackStack() }
            )
        }
    }
}
