package io.horizontalsystems.ethereumkit.core

import io.horizontalsystems.hdwalletkit.Curve
import io.horizontalsystems.hdwalletkit.HDKey
import io.horizontalsystems.hdwalletkit.HDKeychain
import io.horizontalsystems.hdwalletkit.HDPublicKey
import io.horizontalsystems.hdwalletkit.HDWallet

class HDWalletDelegate(
	private val hdKeychain: HDKeychain,
	private val coinType: Int,
	purpose: HDWallet.Purpose,
	private val isBaoCoinWallet: Boolean,
	private val anBaoCoinType: Int = -1
) {

	constructor(
			seed: ByteArray,
			coinType: Int,
			purpose: HDWallet.Purpose,
			isBaoCoinWallet: Boolean,
			curve: Curve = Curve.Secp256K1,
			anBaoCoinType: Int = -1
	) : this(
			HDKeychain(seed, curve), coinType, purpose, isBaoCoinWallet, anBaoCoinType
	)

	constructor(
			masterKey: HDKey,
			coinType: Int,
			purpose: HDWallet.Purpose,
			isBaoCoinWallet: Boolean,
			curve: Curve = Curve.Secp256K1,
			anBaoCoinType: Int = -1
	) : this(
			HDKeychain(masterKey, curve), coinType, purpose, isBaoCoinWallet, anBaoCoinType
	)

	private val hdWallet = HDWallet(hdKeychain, coinType, purpose)

	// m / purpose' / coin_type' / account' / change / address_index
	//
	// Purpose is a constant set to 44' (or 0x8000002C) following the BIP43 recommendation.
	// It indicates that the subtree of this node is used according to this specification.
	// Hardened derivation is used at this level.
	private val purpose: Int = purpose.value

	// One master node (seed) can be used for unlimited number of independent cryptocoins such as Bitcoin, Litecoin or Namecoin. However, sharing the same space for various cryptocoins has some disadvantages.
	// This level creates a separate subtree for every cryptocoin, avoiding reusing addresses across cryptocoins and improving privacy issues.
	// Coin type is a constant, set for each cryptocoin. Cryptocoin developers may ask for registering unused number for their project.
	// The list of already allocated coin types is in the chapter "Registered coin types" below.
	// Hardened derivation is used at this level.
	// network.name == MainNet().name ? 0 : 1
	// private var coinType: Int = 0

	fun hdPublicKey(account: Int, index: Int, external: Boolean): HDPublicKey {
		return if (isBaoCoinWallet && anBaoCoinType != -1) {
			HDPublicKey(privateKeyAnBao(account = account, index = index, chain = if (external) 0 else 1))
		} else {
			HDPublicKey(privateKey(account = account, index = index, chain = if (external) 0 else 1))
		}
	}

	fun hdPublicKeys(account: Int, indices: IntRange, external: Boolean): List<HDPublicKey> {
		val parentPrivateKey = if (isBaoCoinWallet && anBaoCoinType != -1) {
			privateKey("m/$purpose'/0'/0'/$anBaoCoinType")
		} else {
			privateKey("m/$purpose'/$coinType'/$account'/${if (external) 0 else 1}")
		}
		return hdKeychain.deriveNonHardenedChildKeys(parentPrivateKey, indices).map {
			HDPublicKey(it)
		}
	}

	fun receiveHDPublicKey(account: Int, index: Int): HDPublicKey {
		return if (isBaoCoinWallet && anBaoCoinType != -1) {
			HDPublicKey(privateKeyAnBao(account = account, index = index, chain = 0))
		} else {
			HDPublicKey(privateKey(account = account, index = index, chain = 0))
		}
	}

	fun changeHDPublicKey(account: Int, index: Int): HDPublicKey {
		return if (isBaoCoinWallet && anBaoCoinType != -1) {
			HDPublicKey(privateKeyAnBao(account = account, index = index, chain = 1))
		} else {
			HDPublicKey(privateKey(account = account, index = index, chain = 1))
		}
	}

	fun privateKey(account: Int, index: Int, chain: Int): HDKey {
		return privateKey(path = "m/$purpose'/$coinType'/$account'/$chain/$index")
	}

	fun privateKeyAnBao(account: Int, index: Int, chain: Int): HDKey {
		return privateKey(path = "m/$purpose'/0'/0'/$anBaoCoinType/$index")
	}

	fun privateKeyAnBaoSign(account: Int, index: Int, chain: Int): HDKey {
		return privateKey(path = "m/$purpose'/0'/0'/$anBaoCoinType/$index")
	}

	fun privateKey(account: Int): HDKey {
		return privateKey(path = "m/$purpose'/$coinType'/$account'")
	}

	fun privateKey(account: Int, index: Int, external: Boolean): HDKey {
		val chain = if (external) HDWallet.Chain.EXTERNAL.ordinal else HDWallet.Chain.INTERNAL.ordinal
		return if (isBaoCoinWallet && anBaoCoinType != -1) {
			privateKeyAnBao(account, index, chain)
		} else {
			privateKey(
					account, index, chain
			)
		}
	}

	fun privateKey(path: String): HDKey {
		return hdKeychain.getKeyByPath(path)
	}

}