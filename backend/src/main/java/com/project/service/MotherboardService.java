package com.project.service;

import java.net.Socket;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.chocosolver.solver.Model;
import org.chocosolver.solver.Solver;
import org.chocosolver.solver.variables.IntVar;



import com.project.controller.contracts.CPUContract;
import com.project.controller.contracts.CaseContract;
import com.project.controller.contracts.MemoryContract;
import com.project.controller.contracts.PowerSupplyContract;
import com.project.converter.SockerMbToRam;
import com.project.converter.SocketMbToCpu;
import com.project.repository.MotherboardsRepository;
import com.project.repository.ProductConfigRepository;
import com.project.repository.entity.Cpu;
import com.project.repository.entity.Motherboard;
import com.project.repository.entity.ProductConfig;

import org.jboss.logging.Logger;
import org.kie.internal.runtime.manager.context.CaseContext;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;

@ApplicationScoped
public class MotherboardService {
	// This class is a placeholder for the actual implementation of the MotherboardService.
	// It should contain methods to handle business logic related to motherboards.


	private ProductConfig getOrCreate(String sessionId)
	{
		ProductConfig productConfig = productConfigRepository.find("sessionId", sessionId).firstResult();
		if (productConfig == null)
		{
			productConfig = new ProductConfig();
			productConfig.sessionId = sessionId;
			productConfigRepository.persist(productConfig);
		}
		return productConfig;
	}


	private static final Logger logger = Logger.getLogger(MotherboardService.class);

	@Inject
	ProductConfigRepository productConfigRepository;


	@Inject
	SocketMbToCpu mbCpuConverter;
	
	@Inject
	SockerMbToRam socketMbToRamConverter;

	@Inject
	MotherboardsRepository motherboardsRepository;

	public List<Motherboard> filterMotherboard(String sessionId) {
        
    	Model model = new Model("Motherboard Compatibility Check");
        // List<MotherboardContract> compatibleMotherboards = new ArrayList<>();
        List<Motherboard> allMotherboards = motherboardsRepository.listAll();
        List<Motherboard> compatibleMotherboards = new ArrayList<>();


		ProductConfig productConfig = getOrCreate(sessionId);

		CPUContract cpu = productConfig.cpu;
		MemoryContract ram = productConfig.memory;

		int ramSlots = 0;
		int ramQuantity = 0;
		String ramMemoryType = null;
		String ramSpeed = null;
		if (ram != null)
		{
			String[] ramData = ram.getModules().split(" x ");
			ramSlots = Integer.parseInt(ramData[0]);
			
			Pattern pattern = Pattern.compile("(\\\\d+)([a-zA-Z]+)");
			Matcher matcher = pattern.matcher(ramData[1]);

			ramQuantity = Integer.parseInt(matcher.group(1));
			ramMemoryType = matcher.group(2);

			ramSpeed = ram.getSpeed().split(" ")[0];	
		}
		CaseContract cases = productConfig.pcCase;
		PowerSupplyContract powerSupply = productConfig.powerSupply;

		int wattage = 0;
		if (powerSupply != null)
		{
			wattage = Integer.parseInt(powerSupply.getWattage().split(" ")[0]);
		}


        IntVar[] motherboardVars = new IntVar[allMotherboards.size()];
        logger.info("Cpu: " + cpu.getMicroarchitecture());

        for (int i = 0; i < allMotherboards.size(); i++) {
			Motherboard mb = allMotherboards.get(i);
			Boolean isCompatible = true;
            if (cpu != null)
            {
                isCompatible &= mbCpuConverter.socketArchitectureMap.get(mb.socketCpu).contains(cpu.getMicroarchitecture());
			}
			if (isCompatible && ram != null)
			{
				int MbMemoryMax = Integer.parseInt(mb.maxMemory.split(" ")[0]);
				String MbMemoryType = mb.maxMemory.split(" ")[1];
				int MbMemorySlot = Integer.parseInt(mb.memorySlots);

				
				isCompatible &= socketMbToRamConverter.socketMemoryMap.get(mb.socketCpu).contains(ramSpeed);
				isCompatible &= ramMemoryType.equals(MbMemoryType);
				isCompatible &= MbMemorySlot >= ramSlots;
				isCompatible &= MbMemoryMax >= ramQuantity * ramSlots;
			}
			if (isCompatible && cases != null)
			{
				String[] keyWordsCase = cases.getType().split(" ");
				for (String keyWord : keyWordsCase)
				{
					isCompatible &= mb.formFactor.toLowerCase().contains(keyWord.toLowerCase());
				}
			}
			if (isCompatible && powerSupply != null)
			{
				isCompatible &= mb.powerConsumption + productConfig.PowerConsumption <= wattage;
			}

			motherboardVars[i] = model.intVar("mb_" + i, isCompatible ? 1 : 0);
        }
        
        Solver solver = model.getSolver();

        if (solver.solve()) {
            for (int i = 0; i < motherboardVars.length; i++) {
                if (motherboardVars[i].getValue() == 1) {
                    compatibleMotherboards.add(allMotherboards.get(i));
                }
            }
        }

        return compatibleMotherboards;
    }
}