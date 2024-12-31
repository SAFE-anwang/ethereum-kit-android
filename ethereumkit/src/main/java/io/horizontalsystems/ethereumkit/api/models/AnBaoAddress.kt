package io.horizontalsystems.ethereumkit.api.models

import io.horizontalsystems.ethereumkit.models.Address
import java.math.BigInteger

class AnBaoAddress(
		val address: Address,
		val privateKey: BigInteger,
		val balance: BigInteger
) {
}